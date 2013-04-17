package utils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.DBFunctions;
import lazyj.Format;
import lia.Monitor.monitor.AppConfig;
import alien.catalogue.GUID;
import alien.catalogue.GUIDUtils;
import alien.catalogue.PFN;
import alien.catalogue.access.AccessTicket;
import alien.catalogue.access.AccessType;
import alien.catalogue.access.XrootDEnvelope;
import alien.config.ConfigUtils;
import alien.io.protocols.Factory;
import alien.io.protocols.Xrootd;
import alien.io.xrootd.envelopes.XrootDEnvelopeSigner;
import alien.se.SE;
import alien.se.SEUtils;

/**
 * Go over the orphan_pfns and try to physically remove the entries
 * 
 * @author costing
 *
 */
public class OrphanPFNsCleanup {
	/**
	 * logger
	 */
	static transient final Logger logger = ConfigUtils.getLogger(OrphanPFNsCleanup.class.getCanonicalName());
	
	/**
	 * Thread pool per SE
	 */
	static final Map<Integer, ThreadPoolExecutor> EXECUTORS = new ConcurrentHashMap<Integer, ThreadPoolExecutor>(); 

	/**
	 * One thread per SE
	 */
	static Map<Integer, SEThread> SE_THREADS = new ConcurrentHashMap<Integer, SEThread>();
	
	/**
	 * Whether or not the stats have changed and they should be printed on screen
	 */
	volatile static boolean dirtyStats = true; 
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		AppConfig.getProperty("lia.Monitor.group");	// initialize it
		
		final DBFunctions db = ConfigUtils.getDB("alice_users");

		final Xrootd x = Factory.xrootd;
		x.setUseXrdRm(true);

		long lastCheck = 0;
		
		while (true){
			if (System.currentTimeMillis() - lastCheck > 1000*60*30){
				db.query("SELECT distinct se FROM orphan_pfns WHERE fail_count<10;");
		
				while (db.moveNext()){
					final Integer se = Integer.valueOf(db.geti(1));
					
					final SE theSE = SEUtils.getSE(se);
					
					if (theSE==null){
						System.err.println("No such SE: "+se);
						continue;
					}
					
					if (!SE_THREADS.containsKey(se)){
						if (logger.isLoggable(Level.FINE))
							logger.log(Level.FINE, "Starting SE thread for "+se+" ("+theSE.seName+")");
						
						final SEThread t = new SEThread(se.intValue());
						
						t.start();
						
						SE_THREADS.put(se, t);
					}
				}
				
				lastCheck = System.currentTimeMillis();
			}
				
			try{
				Thread.sleep(1000*5);
			}
			catch (InterruptedException ie){
				// ignore
			}
			
			final long count = reclaimedCount.getAndSet(0);
			final long size = reclaimedSize.getAndSet(0);
			
			if (count>0)
				db.query("UPDATE orphan_pfns_status SET status_value=status_value+1 WHERE status_key='reclaimedc';");
			
			if (size>0)
				db.query("UPDATE orphan_pfns_status SET status_value=status_value+"+size+" WHERE status_key='reclaimedb';");
			
			if (dirtyStats){
				System.err.println("Removed: "+removed+" ("+Format.size(reclaimedSpace.longValue())+"), failed to remove: "+failed+" (delta: "+count+" files, "+Format.size(size)+"), sem. status: "+concurrentQueryies.availablePermits());
				dirtyStats = false;
			}
		}
	}
	
	private static final class SEThread extends Thread {
		final int seNumber;
		
		public SEThread(final int seNumber) {
			this.seNumber = seNumber;
		}
		
		@Override
		public void run() {
			setName("SEThread ("+seNumber+")");
			
			final DBFunctions db = ConfigUtils.getDB("alice_users");
			
			ThreadPoolExecutor executor = EXECUTORS.get(Integer.valueOf(seNumber));
						
			while (true){
				concurrentQueryies.acquireUninterruptibly();
				
				try{
					// TODO : what to do with these PFNs ? Iterate over them and release them from the catalogue nevertheless ?
//					db.query("DELETE FROM orphan_pfns WHERE se="+seNumber+" AND fail_count>10;");
					
					db.query("SELECT binary2string(guid),size,md5sum,pfn FROM orphan_pfns WHERE se=? AND fail_count<10 ORDER BY fail_count ASC LIMIT 10000;", false, Integer.valueOf(seNumber));
				}
				finally{
					concurrentQueryies.release();
				}
				
				if (!db.moveNext()){
					// there are no tasks for this SE now, check again sometime later
					
					if (logger.isLoggable(Level.FINE))
						logger.log(Level.FINE, "No more PFNs to clean up for "+seNumber+", freeing the respective thread and executor for now");
					
					if (executor!=null){
						executor.shutdown();
				
						EXECUTORS.remove(Integer.valueOf(seNumber));
					}
					
					SE_THREADS.remove(Integer.valueOf(seNumber));

					return;
				}
				
				if (executor==null){
					// lazy init of the thread pool
					executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(ConfigUtils.getConfig().geti("utils.OrphanPFNsCleanup.threadsPerSE", 16), new ThreadFactory(){
						@Override
						public Thread newThread(final Runnable r) {
							final Thread t = new Thread(r);
							t.setName("Cleanup of "+seNumber);
							
							return t;
						}
					});
					
					executor.setKeepAliveTime(1, TimeUnit.MINUTES);	// 1 minute activity timeout
					
					executor.allowCoreThreadTimeOut(true);
					
					EXECUTORS.put(Integer.valueOf(seNumber), executor);				
				}
				
				do {
					executor.submit(new CleanupTask(db.gets(1), seNumber, db.getl(2), db.gets(3), db.gets(4)));
				}
				while (db.moveNext());
				
				while (executor.getQueue().size()>0 || executor.getActiveCount()>0){
					try{
						Thread.sleep(5000);
					}
					catch (InterruptedException ie){
						// ignore
					}
				}
			}
		}
	}
	
	/**
	 * Number of files kept
	 */
	static final AtomicInteger kept = new AtomicInteger();
	
	/**
	 * Number of failed attempts
	 */
	static final AtomicInteger failed = new AtomicInteger();
	
	/**
	 * Number of successfully removed files 
	 */
	static final AtomicInteger removed = new AtomicInteger();
	
	/**
	 * Amount of space reclaimed
	 */
	static final AtomicLong reclaimedSpace = new AtomicLong();
	
	/**
	 * Fail one file
	 */
	static final void failOne(){
		failed.incrementAndGet();
		
		dirtyStats = true;
	}
	
	private static final AtomicLong reclaimedCount = new AtomicLong();
	private static final AtomicLong reclaimedSize = new AtomicLong(0);
	
	/**
	 * Successful deletion of one file
	 * 
	 * @param size
	 */
	static final void successOne(final long size){
		removed.incrementAndGet();	
		reclaimedCount.incrementAndGet();
				
		if (size>0){
			reclaimedSpace.addAndGet(size);
			reclaimedSize.addAndGet(size);
		}
		
		dirtyStats = true;
	}
	
	/**
	 * Lock for a fixed number of DB queries in parallel 
	 */
	static final Semaphore concurrentQueryies = new Semaphore(ConfigUtils.getConfig().geti("utils.OrphanPFNsCleanup.concurrentQueries", 32));
	
	private static class CleanupTask implements Runnable{
		final String sGUID;
		final int seNumber;
		final long size;
		final String md5;
		final String knownPFN;
		
		public CleanupTask(final String sGUID, final int se, final long size, final String md5, final String knownPFN) {
			this.sGUID = sGUID;
			this.seNumber = se;
			this.size = size;
			this.md5 = md5;
			this.knownPFN = knownPFN;
		}
		
		@Override
		public void run() {
			final UUID uuid = UUID.fromString(sGUID);
			
			final GUID guid;
			
			concurrentQueryies.acquireUninterruptibly();
			
			try{
				guid = GUIDUtils.getGUID(uuid, true);
			}
			finally{
				concurrentQueryies.release();
			}
			
			final SE se = SEUtils.getSE(seNumber);
			
			if (se==null){
				System.err.println("Cannot find any se with seNumber="+seNumber);
				kept.incrementAndGet();
				return;
			}
			
			if (!guid.exists()){
				guid.size = size>0 ? size : 123456;
				guid.md5 = md5!=null ? md5 : "130254d9540d6903fa6f0ab41a132361";
			}
			
			final PFN pfn;
			
			try{
				pfn = knownPFN == null || knownPFN.length()==0 ? new PFN(guid, se) : new PFN(knownPFN, guid, se);
			}
			catch (final Throwable t){
				System.err.println("Cannot generate the entry for "+seNumber+" ("+se.getName()+") and "+sGUID);
				t.printStackTrace();
				
				kept.incrementAndGet();
				return;
			}
			
			final XrootDEnvelope env = new XrootDEnvelope(AccessType.DELETE, pfn);
			
			try {
				if (se.needsEncryptedEnvelope){
					XrootDEnvelopeSigner.encryptEnvelope(env);
				}
				else{
					// new xrootd implementations accept signed-only envelopes
					XrootDEnvelopeSigner.signEnvelope(env);	
				}
			}
			catch (final GeneralSecurityException e) {
				e.printStackTrace();
				return;
			}
			
			pfn.ticket = new AccessTicket(AccessType.DELETE, env);

			final DBFunctions db2 = ConfigUtils.getDB("alice_users");
			
			try {
				if (!Factory.xrootd.delete(pfn)){
					System.err.println("Could not delete from "+se.getName());
			
					concurrentQueryies.acquireUninterruptibly();
					try{
						db2.query("UPDATE orphan_pfns SET fail_count=fail_count+1 WHERE guid=string2binary(?) AND se=?;", false, sGUID, Integer.valueOf(seNumber));
					
						failOne();
					}
					finally{
						concurrentQueryies.release();
					}
				}
				else{
					concurrentQueryies.acquireUninterruptibly();
				
					try{
						if (guid.exists()){
							//System.err.println("Successfully deleted one file of "+Format.size(guid.size)+" from "+se.getName());
							successOne(guid.size);
							
							if (guid.removePFN(se, false)!=null){	// we have just physically deleted this entry, do _not_ queue this pfn again
								if (guid.getPFNs().size()==0){
									if (guid.delete(false)){	// already purged all entries
										//System.err.println("  Deleted the GUID since this was the last replica");
									}
									else{
										System.err.println("  Failed to delete the GUID even if this was the last replica:\n"+guid);
									}
								}
								else{
									//System.err.println("  Kept the GUID since it still has "+guid.getPFNs().size()+" replicas");
								}
							}
							else{
								System.err.println("Failed to remove the replica on "+se.getName()+" from "+guid.guid);
							}
						}
						else{
							successOne(size);
							
							System.err.println("Successfuly deleted from "+se.getName()+" but GUID "+guid.guid+" doesn't exist in the catalogue ...");
						}			
											
						db2.query("DELETE FROM orphan_pfns WHERE guid=string2binary(?) AND se=?;", false, sGUID, Integer.valueOf(seNumber));
					}
					finally{
						concurrentQueryies.release();
					}
				}
			}
			catch (final IOException e) {
				//e.printStackTrace();
				
				failOne();
				
				System.err.println("Exception deleting from "+se.getName()+" : "+e.getMessage());
				
				if (logger.isLoggable(Level.FINE))
					logger.log(Level.FINE, "Exception deleting from "+se.getName(), e);
				
				concurrentQueryies.acquireUninterruptibly();
				
				try{
					db2.query("UPDATE orphan_pfns SET fail_count=fail_count+1 WHERE guid=string2binary(?) AND se=?;", false, sGUID, Integer.valueOf(seNumber));				
				}
				finally{
					concurrentQueryies.release();
				}
			}
		}
	}
	
}
