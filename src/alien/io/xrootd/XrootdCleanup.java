package alien.io.xrootd;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import lazyj.Format;

import alien.catalogue.GUID;
import alien.catalogue.GUIDUtils;
import alien.catalogue.PFN;
import alien.catalogue.access.AccessTicket;
import alien.catalogue.access.AccessType;
import alien.catalogue.access.XrootDEnvelope;
import alien.io.protocols.Xrootd;
import alien.se.SE;
import alien.se.SEUtils;
import alien.services.XrootDEnvelopeSigner;

/**
 * @author costing
 *
 */
public class XrootdCleanup {
	/**
	 * Storage element we are working on
	 */
	final SE se;
	
	private final String server;
	
	private long sizeRemoved = 0;
	private long sizeKept = 0;
	private long filesRemoved = 0;
	private long filesKept = 0;
	private long dirsSeen = 0;
	
	/**
	 * How many items are currently in progress
	 */
	final AtomicInteger inProgress = new AtomicInteger(0);
	
	/**
	 * how many files were processed so far
	 */
	final AtomicInteger processed = new AtomicInteger(0);
	
	private final boolean dryRun;
	
	/**
	 * Check all GUID files in this storage by listing recursively its contents.
	 * 
	 * @param sSE
	 * @param dryRun 
	 * @param threads 
	 */
	public XrootdCleanup(final String sSE, final boolean dryRun, final int threads){
		this.dryRun = dryRun;
		this.XROOTD_THREADS = threads;
		
		se = SEUtils.getSE(sSE);
		
		if (se==null){
			server = null;
			
			System.err.println("No such SE "+sSE);
			
			return;
		}		
		
		String sBase = se.seioDaemons;
		
		if (sBase.startsWith("root://"))
			sBase = sBase.substring(7);
		
		server = sBase;
		
		pushDir("/");
		
		int progressCounter = 0;
		
		while (inProgress.intValue()>0){
			try{
				Thread.sleep(1000);
			}
			catch (InterruptedException ie){
				// ignore
			}
			
			if ( (++progressCounter) % 10 == 0){
				System.err.println("*** "+se.seName+" *** processed so far : "+processed.get()+", "+inProgress.get()+" are queued, "+toString());
			}
		}
		
		for (final CleanupThread t: workers){
			t.signalStop();
			t.interrupt();
		}
	}
	
	/**
	 * processing queue
	 */
	LinkedBlockingQueue<String> processingQueue = null;
	
	private static final int XROOTD_THREADS_DEFAULT = 100;
	
	private int XROOTD_THREADS = XROOTD_THREADS_DEFAULT;
	
	/**
	 * parallel xrootd listing threads
	 */
	private List<CleanupThread> workers = null;
	
	private class CleanupThread extends Thread {
		private boolean shouldRun = true;
		
		public CleanupThread() {
			setDaemon(true);
		}

		@Override
		public void run() {
			while (shouldRun){
				try{
					final String dir = processingQueue.take();
					
					setName(se.seName+dir);
					
					if (dir!=null){
						try{
							storageCleanup(dir);
						}
						finally{
							inProgress.decrementAndGet();
						}
					}
				}
				catch (InterruptedException ie){
					// ignore
				}
			}
		}
		
		/**
		 * Tell it to stop
		 */
		public void signalStop(){
			shouldRun = false;
		}
	}
	
	private synchronized void pushDir(final String dir){
		if (processingQueue == null){
			processingQueue = new LinkedBlockingQueue<String>();
			
			workers = new ArrayList<CleanupThread>(XROOTD_THREADS);
			
			for (int i=0; i<XROOTD_THREADS; i++){
				final CleanupThread t = new CleanupThread();
				t.start();
				
				workers.add(t);
			}
		}
		
		inProgress.incrementAndGet();
		
		processingQueue.offer(dir);
	}
	
	/**
	 * @param path
	 */
	void storageCleanup(final String path){
		System.err.println("storageCleanup: "+path);
		
		dirsSeen++;
		
		try{
			final XrootdListing listing = new XrootdListing(server, path);
			
			for (final XrootdFile file: listing.getFiles()){
				fileCheck(file);
			}
			
			for (final XrootdFile dir: listing.getDirs()){
				if (dir.path.matches("^/\\d{2}(/\\d{5})?$")){
					pushDir(dir.path);
				}
			}
		}
		catch (IOException ioe){
			System.err.println(ioe.getMessage());
			ioe.printStackTrace();
		}
	}
	
	private boolean removeFile(final XrootdFile file){
		if (!dryRun)
			return removeFile(file, se);
		
		System.err.println("WOULD RM "+file);
		
		return true;
	}
	
	/**
	 * @param file
	 * @param se 
	 * @return true if the file was actually removed
	 */
	public static boolean removeFile(final XrootdFile file, final SE se){
		System.err.println("RM "+file);
		
		final GUID guid = GUIDUtils.getGUID(UUID.fromString(file.getName()), true);
		
		guid.size = file.size;
		guid.md5 = "130254d9540d6903fa6f0ab41a132361";
		
		final PFN pfn = new PFN(guid, se);
				
		final XrootDEnvelope env =  new XrootDEnvelope(AccessType.DELETE, pfn);

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
			return false;
		}
		
		pfn.ticket = new AccessTicket(AccessType.DELETE, env);
		
		final Xrootd xrootd = new Xrootd();
		try {
			if (!xrootd.delete(pfn)){
				System.err.println("Could not delete : "+pfn);
				return false;
			}
			
			return true;
		}
		catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private void fileCheck(final XrootdFile file) {
		try{
			if (System.currentTimeMillis() - file.date.getTime() < 1000*60*60*24){
				// ignore very recent files
				return;
			}
			
			final UUID uuid = UUID.fromString(file.getName());
			
			final GUID guid = GUIDUtils.getGUID(uuid);
			
			boolean remove = false;
			
			if (guid==null){
				remove = true;
			}
			else{
				final Set<PFN> pfns = guid.getPFNs();
				
				if (pfns==null || pfns.size()==0)
					remove = true;
				else{
					boolean found = false;
					
					for (final PFN pfn: pfns){
						if (pfn.seNumber == se.seNumber){
							found = true;
							break;
						}
					}
					
					remove = !found;
				}
			}
			
			if (remove && removeFile(file)){
				sizeRemoved += file.size;
				filesRemoved ++;
			}
			else{
				sizeKept += file.size;
				filesKept ++;
			}
		}
		catch (Exception e){
			// ignore
		}
		
		processed.incrementAndGet();
	}
	
	@Override
	public String toString() {
		return "Removed "+filesRemoved+" files ("+Format.size(sizeRemoved)+"), kept "+filesKept+" ("+Format.size(sizeKept)+"), "+dirsSeen+" directories";
	}
	
	/**
	 * @param args the only argument taken by this class is the name of the storage to be cleaned
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		final OptionParser parser = new OptionParser();
		
		parser.accepts("n", "Do not take any action (dry run)");
		parser.accepts("?", "Print this help");
		parser.accepts("t").withRequiredArg().describedAs("Parallel threads, default "+XROOTD_THREADS_DEFAULT).ofType(Integer.class);
		
		final OptionSet options = parser.parse(args);
		
		if (options.nonOptionArguments().size()==0 || options.has("?")){
			parser.printHelpOn(System.out);
			return;
		}
		
		final boolean dryRun = options.has("n");
		
		System.err.println("Dry run : "+dryRun);
		
		final long lStart = System.currentTimeMillis();
		
		int threads = XROOTD_THREADS_DEFAULT;
		
		if (options.has("t") && options.hasArgument("t"))
			threads = ((Integer) options.valueOf("t")).intValue();
		
		System.err.println("Parallel threads : "+threads);
		
		for (String se: options.nonOptionArguments()){
			final XrootdCleanup cleanup = new XrootdCleanup(se, dryRun, threads);
			System.err.println(cleanup+", took "+Format.toInterval(System.currentTimeMillis() - lStart));
		}
	}
	
}
