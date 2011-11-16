/**
 * 
 */
package alien.io.protocols;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.LRUMap;
import alien.catalogue.GUID;
import alien.config.ConfigUtils;
import alien.io.IOUtils;

/**
 * @author costing
 * @since Nov 14, 2011
 */
public class TempFileManager extends LRUMap<GUID, File>{

	/**
	 * 
	 */
	private static final long serialVersionUID = -6481164994092554757L;

	/**
	 * Logger
	 */
	static transient final Logger logger = ConfigUtils.getLogger(TempFileManager.class.getCanonicalName());
	
	private final long totalSizeLimit;
	
	private long currentSize = 0;
	
	private final boolean delete;
	
	private TempFileManager(final int entriesLimit, final long sizeLimit, final boolean delete) {
		super(entriesLimit);

		this.totalSizeLimit = sizeLimit;
		
		this.delete = delete;
	}

	private static final TempFileManager tempInstance = new TempFileManager(ConfigUtils.getConfig().geti("alien.io.protocols.TempFileManager.temp.entries", 1000), ConfigUtils.getConfig().getl("alien.io.protocols.TempFileManager.temp.size", 10*1024*1024*1024), true);
	private static final TempFileManager persistentInstance = new TempFileManager(ConfigUtils.getConfig().geti("alien.io.protocols.TempFileManager.temp.entries", 1000), 0, false);
	
	private static List<File> lockedLocalFiles = new LinkedList<File>();
	
	/* (non-Javadoc)
	 * @see lazyj.LRUMap#removeEldestEntry(java.util.Map.Entry)
	 */
	@Override
	protected boolean removeEldestEntry(final java.util.Map.Entry<GUID, File> eldest) {
		boolean remove = !lockedLocalFiles.contains(eldest.getValue()) && (super.removeEldestEntry(eldest) || (totalSizeLimit>0 && currentSize > totalSizeLimit));
	
		if (remove){
			currentSize -= eldest.getKey().size;
			
			if (delete && !eldest.getValue().delete())
				logger.log(Level.WARNING, "Could not delete temporary file "+eldest.getValue());
		}
		
		return remove;
	}
	
	/* (non-Javadoc)
	 * @see java.util.HashMap#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public File put(final GUID key, final File value) {
		if (delete)
			value.deleteOnExit();
		
		currentSize += key.size;
		
		return super.put(key, value);
	}
	
	/**
	 * @param key
	 * @return the cached file
	 */
	public static File getAny(final GUID key){
		File f = tempInstance.get(key);
		
		if (f!=null && f.exists() && f.isFile() && f.canRead()){
			lock(f);
			
			return f;
		}
		
		f = persistentInstance.get(key);
		
		try {
			if (f!=null && f.exists() && f.isFile() && f.canRead() && f.length()==key.size && IOUtils.getMD5(f).equals(key.md5))
				return f;
		}
		catch (IOException e) {
			return null;
		}
		
		return null;
	}
	
	/**
	 * @param key
	 * @param localFile
	 */
	public static void putTemp(final GUID key, final File localFile){
		tempInstance.put(key, localFile);
		
		lock(localFile);
	}
	
	/**
	 * @param f
	 */
	public static void lock(final File f){
		lockedLocalFiles.add(f);
	}
	
	/**
	 * @param f
	 */
	public static void release(final File f){
		lockedLocalFiles.remove(f);
	}
	
	/**
	 * @param key
	 * @param localFile
	 */
	public static void putPersistent(final GUID key, final File localFile){
		persistentInstance.put(key, localFile);
	}
}