/**
 *
 */
package alien.io.protocols;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import alien.catalogue.GUID;
import alien.catalogue.GUIDUtils;
import alien.config.ConfigUtils;
import alien.io.IOUtils;
import lazyj.LRUMap;

/**
 * @author costing
 * @since Nov 14, 2011
 */
public class TempFileManager extends LRUMap<GUID, File> {

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

	private static final TempFileManager tempInstance = new TempFileManager(ConfigUtils.getConfig().geti("alien.io.protocols.TempFileManager.temp.entries", 50),
			ConfigUtils.getConfig().getl("alien.io.protocols.TempFileManager.temp.size", 10 * 1024 * 1024 * 1024L), true);
	private static final TempFileManager persistentInstance = new TempFileManager(ConfigUtils.getConfig().geti("alien.io.protocols.TempFileManager.persistent.entries", 100), 0, false);

	private static List<File> lockedLocalFiles = new LinkedList<>();

	/*
	 * (non-Javadoc)
	 *
	 * @see lazyj.LRUMap#removeEldestEntry(java.util.Map.Entry)
	 */
	@Override
	protected boolean removeEldestEntry(final java.util.Map.Entry<GUID, File> eldest) {
		final boolean wasLocked = isLocked(eldest.getValue());

		final boolean remove = !eldest.getValue().exists() || (!wasLocked && (super.removeEldestEntry(eldest) || (this.totalSizeLimit > 0 && this.currentSize > this.totalSizeLimit)));

		if (logger.isLoggable(Level.FINEST))
			logger.log(Level.FINEST, "Decision on ('" + eldest.getValue().getAbsolutePath() + "'): " + remove + ", count: " + size() + " / " + getLimit() + ", size: " + this.currentSize + " / "
					+ this.totalSizeLimit + ", locked: " + wasLocked);

		if (remove) {
			this.currentSize -= eldest.getKey().size;

			if (this.delete) {
				if (eldest.getValue().exists()) {
					if (!eldest.getValue().delete())
						logger.log(Level.WARNING, "Could not delete temporary file " + eldest.getValue());
				}
				else
					logger.log(Level.FINE, "Somebody has already deleted " + eldest.getValue() + " while its lock status was: " + wasLocked);

				release(eldest.getValue());
			}
		}

		return remove;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.util.HashMap#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public File put(final GUID key, final File value) {
		if (this.delete)
			value.deleteOnExit();

		this.currentSize += key.size;

		return super.put(key, value);
	}

	/**
	 * Get the temporary downloaded file with this name
	 *
	 * @param key
	 * @return the temporary file, if it exists, locked (!). Make sure to call {@link #release(File)} after you have finished working with it.
	 */
	public static File getTemp(final GUID key) {
		File f;

		synchronized (tempInstance) {
			f = tempInstance.get(key);
		}

		try {
			if (f != null && f.exists() && f.isFile() && f.canRead() && f.length() == key.size && IOUtils.getMD5(f).equals(key.md5)) {
				lock(f);
				return f;
			}
		}
		catch (final IOException ioe) {
			logger.log(Level.WARNING, "Error computing md5 checksum of " + f.getAbsolutePath(), ioe);
		}

		return null;
	}

	/**
	 * Get the persistent downloaded file with this name
	 *
	 * @param key
	 * @return the temporary file, if it exists.
	 */
	public static File getPersistent(final GUID key) {
		File f;

		synchronized (persistentInstance) {
			f = persistentInstance.get(key);
		}

		try {
			if (f != null && f.exists() && f.isFile() && f.canRead() && f.length() == key.size && IOUtils.getMD5(f).equalsIgnoreCase(key.md5))
				return f;
		}
		catch (@SuppressWarnings("unused") final IOException e) {
			// ignore
		}

		return null;
	}

	/**
	 * @param key
	 * @return the cached file
	 */
	public static File getAny(final GUID key) {
		final File f = getTemp(key);

		if (f != null)
			return f;

		return getPersistent(key);
	}

	/**
	 * @param key
	 * @param localFile
	 */
	public static void putTemp(final GUID key, final File localFile) {
		synchronized (tempInstance) {
			final File old = tempInstance.get(key);

			if (old != null) {
				if (old.exists() && old.length() == key.size) {
					logger.log(Level.FINE, "Refusing to overwrite " + key.guid + " -> " + old + " with " + localFile);
					tempInstance.put(GUIDUtils.createGuid(), localFile);
				}
				else {
					release(old);
					tempInstance.put(key, localFile);
					lock(localFile);
				}
			}
			else {
				tempInstance.put(key, localFile);
				lock(localFile);
			}
		}
	}

	/**
	 * Re-pin a temporary file on disk until its processing is over.
	 *
	 * @param f
	 * @see #release(File)
	 */
	private static void lock(final File f) {
		synchronized (lockedLocalFiles) {
			lockedLocalFiles.add(f);
		}

		if (logger.isLoggable(Level.FINEST))
			try {
				throw new IOException();
			}
			catch (final IOException ioe) {
				logger.log(Level.FINEST, f.getAbsolutePath() + " locked by", ioe);
			}
	}

	/**
	 * For temporary downloaded files, call this when you are done working with the local file to allow the garbage collector to reclaim the space when needed.
	 *
	 * @param f
	 * @return <code>true</code> if this file was indeed released
	 */
	public static boolean release(final File f) {
		if (f == null)
			return false;

		boolean removed;

		synchronized (lockedLocalFiles) {
			removed = lockedLocalFiles.remove(f);
		}

		if ((!removed && logger.isLoggable(Level.FINE)) || logger.isLoggable(Level.FINEST))
			try {
				throw new IOException();
			}
			catch (final IOException ioe) {
				logger.log(Level.FINE, "Asked to release a file " + (removed ? "that was indeed locked: " : "that was not previously locked: ") + f.getAbsolutePath(), ioe);
			}

		return removed;
	}

	/**
	 * @param f
	 * @return <code>true</code> if the file is locked
	 */
	public static boolean isLocked(final File f) {
		synchronized (lockedLocalFiles) {
			return lockedLocalFiles.contains(f);
		}
	}

	/**
	 * @return currently locked files, for debugging purposes
	 */
	public static List<File> getLockedFiles() {
		synchronized (lockedLocalFiles) {
			return new ArrayList<>(lockedLocalFiles);
		}
	}

	/**
	 * @param key
	 * @param localFile
	 */
	public static void putPersistent(final GUID key, final File localFile) {
		synchronized (persistentInstance) {
			persistentInstance.put(key, localFile);
		}
	}

	private static int gc(final TempFileManager instance) {
		int ret = 0;

		synchronized (instance) {
			final Iterator<Map.Entry<GUID, File>> it = instance.entrySet().iterator();

			while (it.hasNext())
				if (instance.removeEldestEntry(it.next())) {
					ret++;
					it.remove();
				}
		}

		return ret;
	}

	/**
	 * Periodically call this method to go through all cached entries and check their validity
	 *
	 * @return number of collected entries
	 */
	static int gc() {
		int ret = gc(tempInstance);
		ret += gc(persistentInstance);

		return ret;
	}

	private static final Thread cleanup = new Thread("alien.io.protocols.TempFileManager.cleanup") {
		@Override
		public void run() {
			while (true) {
				int collected = 0;

				try {
					collected = gc();
				}
				catch (final Throwable t) {
					logger.log(Level.WARNING, "Exception collecting gc", t);
				}

				long sleepTime = ConfigUtils.getConfig().getl("alien.io.protocols.TempFileManager.sleepBetweenGC", 120) * 1000;

				if (sleepTime <= 0)
					sleepTime = 30000;

				if (collected == 0)
					sleepTime *= 2;
				else
					sleepTime = Math.max(sleepTime / collected, 10000);

				try {
					sleep(sleepTime);
				}
				catch (@SuppressWarnings("unused") final InterruptedException e) {
					return;
				}
			}
		}
	};

	static {
		cleanup.setDaemon(true);
		cleanup.start();
	}
}
