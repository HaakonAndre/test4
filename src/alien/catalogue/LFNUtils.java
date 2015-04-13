package alien.catalogue;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.DBFunctions;
import lazyj.Format;
import alien.config.ConfigUtils;
import alien.user.AliEnPrincipal;
import alien.user.AuthorizationChecker;

/**
 * LFN utilities
 * 
 * @author costing
 * 
 */
public class LFNUtils {

	/**
	 * Logger
	 */
	static transient final Logger logger = ConfigUtils.getLogger(LFNUtils.class.getCanonicalName());

	/**
	 * Get an LFN which corresponds to the given GUID
	 * 
	 * @param g
	 * @return one of the matching LFNs, if there is any such entry
	 */
	public static LFN getLFN(final GUID g) {
		if (g == null)
			return null;

		final Set<IndexTableEntry> indextable = CatalogueUtils.getAllIndexTables();

		if (indextable == null)
			return null;

		for (final IndexTableEntry ite : indextable) {
			final LFN l = ite.getLFN(g.guid);

			if (l != null)
				return l;
		}

		return null;
	}

	/**
	 * Get the LFN entry for this catalog filename
	 * 
	 * @param fileName
	 * @return LFN entry
	 */
	public static LFN getLFN(final String fileName) {
		return getLFN(fileName, false);
	}

	/**
	 * Get the LFN entry for this catalog filename, optionally returning an
	 * empty object if the entry doesn't exist (yet)
	 * 
	 * @param fileName
	 * @param evenIfDoesntExist
	 * @return entry
	 */
	public static LFN getLFN(final String fileName, final boolean evenIfDoesntExist) {
		if (fileName == null || fileName.length() == 0)
			return null;

		String processedFileName = fileName;

		while (processedFileName.indexOf("//") >= 0)
			processedFileName = Format.replace(processedFileName, "//", "/");

		processedFileName = Format.replace(processedFileName, "/./", "/");

		int idx = processedFileName.indexOf("/../");

		while (idx > 0) {
			final int idx2 = processedFileName.lastIndexOf("/", idx - 1);

			if (idx2 > 0)
				processedFileName = processedFileName.substring(0, idx2) + processedFileName.substring(idx + 3);

			// System.err.println("After replacing .. : "+processedFileName);

			idx = processedFileName.indexOf("/../");
		}

		if (processedFileName.endsWith("/..")) {
			final int idx2 = processedFileName.lastIndexOf('/', processedFileName.length() - 4);

			if (idx2 > 0)
				processedFileName = processedFileName.substring(0, idx2);
		}

		final IndexTableEntry ite = CatalogueUtils.getClosestMatch(processedFileName);

		if (ite == null) {
			logger.log(Level.FINE, "IndexTableEntry is null for: " + processedFileName + " (even if doesn't exist: " + evenIfDoesntExist + ")");

			return null;
		}

		if (logger.isLoggable(Level.FINER))
			logger.log(Level.FINER, "Using " + ite + " for: " + processedFileName);

		return ite.getLFN(processedFileName, evenIfDoesntExist);
	}

	/**
	 * @param user
	 * @param lfn
	 * @return status of the removal
	 */
	public static boolean rmLFN(final AliEnPrincipal user, final LFN lfn) {
		if (lfn != null && lfn.exists && !lfn.isDirectory()) {
			if (AuthorizationChecker.canWrite(lfn, user)) {
				System.out.println("Unimplemented request from [" + user.getName() + "], rm [" + lfn.getCanonicalName() + "]");
				// TODO
				return false;
			}
			return false;

		}

		return false;
	}

	/**
	 * @param user
	 * @param lfn
	 * @param newpath
	 * @return status of the removal
	 */
	public static LFN mvLFN(final AliEnPrincipal user, final LFN lfn, final String newpath) {
		if (lfn == null || !lfn.exists) {
			logger.log(Level.WARNING, "The file to move doesn't exist.");
			return null;
		}

		if (!AuthorizationChecker.canWrite(lfn.getParentDir(), user)) {
			logger.log(Level.WARNING, "Not authorized to move the file [" + lfn.getCanonicalName() + "]");
			logger.log(Level.WARNING, "YOU ARE [" + user + "]");
			logger.log(Level.WARNING, "parent is [" + lfn.getParentDir() + "]");
			return null;
		}

		final LFN tLFN = getLFN(newpath, true);

		if (tLFN.exists || !tLFN.getParentDir().exists || !AuthorizationChecker.canWrite(tLFN.getParentDir(), user)) {
			logger.log(Level.WARNING, "Not possible to move to [" + tLFN.getCanonicalName() + "]");
			return null;
		}

		tLFN.aclId = lfn.aclId;
		tLFN.broken = lfn.broken;
		tLFN.ctime = lfn.ctime;
		tLFN.dir = lfn.dir;
		tLFN.expiretime = lfn.expiretime;
		tLFN.gowner = lfn.gowner;
		tLFN.guid = lfn.guid;
		tLFN.guidtime = lfn.guidtime;
		tLFN.jobid = lfn.jobid;
		tLFN.md5 = lfn.md5;
		tLFN.owner = lfn.owner;
		tLFN.perm = lfn.perm;
		tLFN.replicated = lfn.replicated;
		tLFN.size = lfn.size;
		tLFN.type = lfn.type;

		if (!LFNUtils.insertLFN(tLFN)) {
			logger.log(Level.WARNING, "Could not insert: " + tLFN);
			return null;
		}

		if (lfn.isDirectory()) {
			final List<LFN> subentries = lfn.list();

			if (subentries != null)
				for (final LFN subentry : subentries)
					if (mvLFN(user, subentry, tLFN.getCanonicalName() + "/" + subentry.getFileName()) == null) {
						logger.log(Level.WARNING, "Could not move " + subentry.getCanonicalName() + " to " + tLFN.getCanonicalName() + "/" + subentry.getFileName() + ", bailing out");
						return null;
					}
		}

		if (!lfn.delete(false, false)) {
			logger.log(Level.WARNING, "Could not delete: " + lfn);
			return null;
		}

		if (logger.isLoggable(Level.FINE))
			logger.log(Level.FINE, "Deleted entry [" + lfn.getCanonicalName() + "]");

		return tLFN;
	}

	/**
	 * Make sure the parent directory exists
	 * 
	 * @param lfn
	 * @return the updated LFN entry
	 */
	static LFN ensureDir(final LFN lfn) {
		if (lfn.exists)
			return lfn;

		if (lfn.perm == null)
			lfn.perm = "755";

		LFN parent = lfn.getParentDir(true);

		if (!parent.exists) {
			parent.owner = lfn.owner;
			parent.gowner = lfn.gowner;
			parent.perm = lfn.perm;
			parent = ensureDir(parent);
		}

		if (parent == null)
			return null;

		lfn.parentDir = parent;
		lfn.type = 'd';

		if (insertLFN(lfn))
			return lfn;

		return null;
	}

	/**
	 * Create a new directory with a given owner
	 * 
	 * @param owner
	 *            owner of the newly created structure(s)
	 * @param path
	 *            the path to be created
	 * @return the (new or existing) directory, if the owner can create it,
	 *         <code>null</code> if the owner is not allowed to do this
	 *         operation
	 */
	public static LFN mkdir(final AliEnPrincipal owner, final String path) {
		return mkdir(owner, path, false);
	}

	/**
	 * Create a new directory hierarchy with a given owner
	 * 
	 * @param owner
	 *            owner of the newly created structure(s)
	 * @param path
	 *            the path to be created
	 * @return the (new or existing) directory, if the owner can create it,
	 *         <code>null</code> if the owner is not allowed to do this
	 *         operation
	 */
	public static LFN mkdirs(final AliEnPrincipal owner, final String path) {
		return mkdir(owner, path, true);
	}

	/**
	 * Create a new directory (hierarchy) with a given owner
	 * 
	 * @param owner
	 *            owner of the newly created structure(s)
	 * @param path
	 *            the path to be created
	 * @param createMissingParents
	 *            if <code>true</code> then it will try to create any number of
	 *            intermediate directories, otherwise the direct parent must
	 *            already exist
	 * @return the (new or existing) directory, if the owner can create it,
	 *         <code>null</code> if the owner is not allowed to do this
	 *         operation
	 */
	public static LFN mkdir(final AliEnPrincipal owner, final String path, final boolean createMissingParents) {
		final LFN lfn = LFNUtils.getLFN(path, true);

		return mkdir(owner, lfn, createMissingParents);
	}

	/**
	 * Create a new directory with a given owner
	 * 
	 * @param owner
	 *            owner of the newly created structure(s)
	 * @param lfn
	 *            the path to be created
	 * @return the (new or existing) directory, if the owner can create it,
	 *         <code>null</code> if the owner is not allowed to do this
	 *         operation
	 */
	public static LFN mkdir(final AliEnPrincipal owner, final LFN lfn) {
		return mkdir(owner, lfn, false);
	}

	/**
	 * Create a new directory hierarchy with a given owner
	 * 
	 * @param owner
	 *            owner of the newly created structure(s)
	 * @param lfn
	 *            the path to be created
	 * @return the (new or existing) directory, if the owner can create it,
	 *         <code>null</code> if the owner is not allowed to do this
	 *         operation
	 */
	public static LFN mkdirs(final AliEnPrincipal owner, final LFN lfn) {
		return mkdir(owner, lfn, true);
	}

	/**
	 * Create a new directory (hierarchy) with a given owner
	 * 
	 * @param owner
	 *            owner of the newly created structure(s)
	 * @param lfn
	 *            the path to be created
	 * @param createMissingParents
	 *            if <code>true</code> then it will try to create any number of
	 *            intermediate directories, otherwise the direct parent must
	 *            already exist
	 * @return the (new or existing) directory, if the owner can create it,
	 *         <code>null</code> if the owner is not allowed to do this
	 *         operation
	 */
	public static LFN mkdir(final AliEnPrincipal owner, final LFN lfn, final boolean createMissingParents) {

		if (owner == null || lfn == null)
			return null;

		if (lfn.exists) {
			if (lfn.isDirectory() && AuthorizationChecker.canWrite(lfn, owner))
				return lfn;

			return null;
		}

		lfn.owner = owner.getName();
		lfn.gowner = lfn.owner;

		lfn.size = 0;

		LFN parent = lfn.getParentDir(true);

		if (!parent.exists && !createMissingParents)
			return null;

		while (parent != null && !parent.exists)
			parent = parent.getParentDir(true);

		if (parent != null && parent.isDirectory() && AuthorizationChecker.canWrite(parent, owner))
			return ensureDir(lfn);

		return null;
	}

	/**
	 * @param user
	 * @param lfn
	 * @return status of the removal
	 */
	public static boolean rmdir(final AliEnPrincipal user, final LFN lfn) {
		if (lfn != null && lfn.exists && lfn.isDirectory()) {
			if (AuthorizationChecker.canWrite(lfn, user)) {
				System.out.println("Unimplemented request from [" + user.getName() + "], rmdir [" + lfn.getCanonicalName() + "]");
				// TODO
				return false;
			}
			return false;

		}

		return false;
	}

	/**
	 * Touch an LFN: if the entry exists, update its timestamp, otherwise try to
	 * create an empty file
	 * 
	 * @param user
	 *            who wants to do the operation
	 * @param lfn
	 *            LFN to be touched (from
	 *            {@link LFNUtils#getLFN(String, boolean)}, called with the
	 *            second argument <code>false</code> if the entry doesn't exist
	 *            yet
	 * @return <code>true</code> if the LFN was touched
	 */
	public static boolean touchLFN(final AliEnPrincipal user, final LFN lfn) {
		if (!lfn.exists) {
			final LFN parentDir = lfn.getParentDir();

			if (parentDir == null || !AuthorizationChecker.canWrite(parentDir, user)) {
				logger.log(Level.SEVERE, "Cannot write to the Current Directory OR Parent Directory is null BUT file exists. Terminating");
				return false;
			}

			lfn.type = 'f';
			lfn.size = 0;
			lfn.md5 = "d41d8cd98f00b204e9800998ecf8427e";
			lfn.guid = null;
			lfn.guidtime = null;
			lfn.owner = user.getName();
			lfn.gowner = user.getRoles().iterator().next();
			lfn.perm = "755";
		} else if (!AuthorizationChecker.canWrite(lfn, user)) {
			logger.log(Level.SEVERE, "Cannot write to the Current Directory BUT file does not exist. Terminating");
			return false;
		}

		lfn.ctime = new Date();

		if (!lfn.exists)
			return insertLFN(lfn);

		return lfn.update();
	}

	/**
	 * Insert an LFN in the catalogue
	 * 
	 * @param lfn
	 * @return true if the entry was inserted (or previously existed), false if
	 *         there was an error
	 */
	static boolean insertLFN(final LFN lfn) {
		if (lfn.exists)
			// nothing to be done, the entry already exists
			return true;

		final IndexTableEntry ite = CatalogueUtils.getClosestMatch(lfn.getCanonicalName());

		if (ite == null) {
			logger.log(Level.WARNING, "IndexTableEntry is null for: " + lfn.getCanonicalName());

			return false;
		}

		final LFN parent = ensureDir(lfn.getParentDir());

		if (parent == null) {
			logger.log(Level.WARNING, "Parent dir is null for " + lfn.getCanonicalName());

			return false;
		}

		lfn.parentDir = parent;
		lfn.indexTableEntry = ite;

		if (lfn.indexTableEntry.equals(parent.indexTableEntry))
			lfn.dir = parent.entryId;

		return lfn.insert();
	}

	/**
	 * the "-s" flag of AliEn `find`
	 */
	public static final int FIND_NO_SORT = 1;

	/**
	 * the "-d" flag of AliEn `find`
	 */
	public static final int FIND_INCLUDE_DIRS = 2;

	/**
	 * the "-y" flag of AliEn `find`
	 */
	public static final int FIND_BIGGEST_VERSION = 4;

	/**
	 * Use Perl-style regexp in the pattern instead of SQL-style (which is the
	 * default for find). This is to be used for wildcard expansion.
	 */
	public static final int FIND_REGEXP = 8;

	/**
	 * @param path
	 * @param pattern
	 * @param flags
	 *            a combination of FIND_* flags
	 * @return the list of LFNs that match
	 */
	public static Collection<LFN> find(final String path, final String pattern, final int flags) {
		final Set<LFN> ret = (flags & FIND_NO_SORT) != 0 ? new LinkedHashSet<LFN>() : new TreeSet<LFN>();

		final Collection<IndexTableEntry> matchingTables = CatalogueUtils.getAllMatchingTables(path);

		final String processedPattern;

		if ((flags & FIND_REGEXP) == 0)
			processedPattern = Format.replace(pattern, "*", "%");
		else
			processedPattern = Format.replace(Format.replace(pattern, "*", "[^/]*"), "?", ".");

		for (final IndexTableEntry ite : matchingTables) {
			final List<LFN> findResults = ite.find(path, processedPattern, flags);

			if (findResults != null && findResults.size() > 0)
				ret.addAll(findResults);
		}

		return ret;
	}

	/**
	 * @param path
	 * @param tag
	 * @return metadata table where this tag can be found for this path, or
	 *         <code>null</code> if there is no such entry
	 */
	public static Set<String> getTagTableNames(final String path, final String tag) {
		final DBFunctions db = ConfigUtils.getDB("alice_data");

		final Set<String> ret = new HashSet<>();
		
		try {
			db.setReadOnly(true);
			
			db.query("SELECT distinct tableName FROM TAG0 WHERE tagName='" + Format.escSQL(tag) + "' AND '" + Format.escSQL(path) + "' LIKE concat(path,'%') ORDER BY length(path) DESC;");

			while (db.moveNext()){
				ret.add(db.gets(1));
			}
		} finally {
			db.close();
		}
		
		return ret;
	}

	/**
	 * @param path
	 * @param pattern
	 * @param tag
	 * @param query
	 * @param flags
	 * @return the files that match the metadata query
	 */
	public static Set<LFN> findByMetadata(final String path, final String pattern, final String tag, final String query, final int flags) {
		DBFunctions db = null;

		final Set<LFN> ret = new LinkedHashSet<>();

		try {
			for (final String tableName : getTagTableNames(path, tag)) {
				if (db == null)
					db = ConfigUtils.getDB("alice_data");
				
				if (db == null){
					logger.log(Level.WARNING, "Cannot get a DB instance");
					
					return ret;
				}

				String q = "SELECT distinct file FROM " + Format.escSQL(tableName) + " " + Format.escSQL(tag) + " WHERE file LIKE '" + Format.escSQL(path + "%" + pattern + "%") + "' AND "
						+ Format.escSQL(query.replace(":", "."));

				if ((flags & FIND_BIGGEST_VERSION) != 0)
					q += " ORDER BY version DESC, entryId DESC LIMIT 1";

				db.setReadOnly(true);
				
				if (!db.query(q))
					continue;

				while (db.moveNext()) {
					final LFN l = LFNUtils.getLFN(db.gets(1));

					if (l != null)
						ret.add(l);
				}
			}
		} finally {
			if (db != null)
				db.close();
		}

		return ret;
	}

	/**
	 * Create a new collection with the given path
	 * 
	 * @param collectionName
	 *            full path (LFN) of the collection
	 * @param owner
	 *            collection owner
	 * @return the newly created collection
	 */
	public static LFN createCollection(final String collectionName, final AliEnPrincipal owner) {
		if (collectionName == null || owner == null)
			return null;

		final LFN lfn = getLFN(collectionName, true);

		if (lfn.exists) {
			if (lfn.isCollection() && AuthorizationChecker.canWrite(lfn, owner))
				return lfn;

			return null;
		}

		final LFN parentDir = lfn.getParentDir();

		if (parentDir == null)
			// will not create directories up to this path, do it explicitly
			// before calling this
			return null;

		if (!AuthorizationChecker.canWrite(parentDir, owner))
			// not allowed to write here. Not sure we should double check here,
			// but it doesn't hurt to be sure
			return null;

		final GUID guid = GUIDUtils.createGuid();

		guid.ctime = lfn.ctime = new Date();
		guid.owner = lfn.owner = owner.getName();

		final Set<String> roles = owner.getRoles();
		guid.gowner = lfn.gowner = (roles != null && roles.size() > 0) ? roles.iterator().next() : lfn.owner;
		guid.size = lfn.size = 0;
		guid.type = lfn.type = 'c';

		lfn.guid = guid.guid;
		lfn.perm = guid.perm = "755";
		lfn.aclId = guid.aclId = -1;
		lfn.jobid = -1;
		lfn.md5 = guid.md5 = "n/a";

		if (!guid.update())
			return null;

		if (!insertLFN(lfn))
			return null;

		final DBFunctions db = ConfigUtils.getDB("alice_data");

		final String q = "INSERT INTO COLLECTIONS (collGUID) VALUES (string2binary(?));";

		try {
			if (!db.query(q, false, lfn.guid.toString()))
				return null;
		} finally {
			db.close();
		}

		return lfn;
	}

	/**
	 * @param collection
	 * @param lfns
	 * @return <code>true</code> if the collection was modified
	 */
	public static boolean removeFromCollection(final LFN collection, final Set<LFN> lfns) {
		if (!collection.exists || !collection.isCollection() || lfns == null || lfns.size() == 0)
			return false;

		final DBFunctions db = ConfigUtils.getDB("alice_data");

		try {
			db.setReadOnly(true);
			
			db.query("SELECT collectionId FROM COLLECTIONS where collGUID=string2binary(?);", false, collection.guid.toString());
			
			db.setReadOnly(false);

			if (!db.moveNext())
				return false;

			final int collectionId = db.geti(1);

			final Set<String> currentLFNs = collection.listCollection();

			final GUID guid = GUIDUtils.getGUID(collection);

			boolean updated = false;

			boolean shouldUpdateSEs = false;

			for (final LFN l : lfns) {
				if (!currentLFNs.contains(l.getCanonicalName()))
					continue;

				if (!db.query("DELETE FROM COLLECTIONS_ELEM where collectionId=? AND origLFN=? AND guid=string2binary(?);", false, Integer.valueOf(collectionId), l.getCanonicalName(),
						l.guid.toString()))
					continue;

				if (db.getUpdateCount() != 1)
					continue;

				guid.size -= l.size;
				updated = true;

				if (!shouldUpdateSEs) {
					final Set<PFN> whereis = l.whereisReal();

					if (whereis != null)
						for (final PFN p : whereis)
							if (!guid.seStringList.contains(Integer.valueOf(p.seNumber))) {
								shouldUpdateSEs = true;
								break;
							}
				}
			}

			if (updated) {
				collection.size = guid.size;

				collection.ctime = guid.ctime = new Date();

				if (shouldUpdateSEs) {
					Set<Integer> ses = null;

					final Set<String> remainingLFNs = collection.listCollection();

					for (final String s : remainingLFNs)
						if (ses == null || ses.size() > 0) {
							final LFN l = LFNUtils.getLFN(s);

							if (l == null)
								continue;

							final Set<PFN> whereis = l.whereisReal();

							final Set<Integer> lses = new HashSet<>();

							for (final PFN pfn : whereis)
								lses.add(Integer.valueOf(pfn.seNumber));

							if (ses != null)
								ses.retainAll(lses);
							else
								ses = lses;
						}

					if (ses != null)
						guid.seStringList = ses;
				}

				guid.update();
				collection.update();

				return true;
			}

			return false;
		} finally {
			db.close();
		}
	}

	/**
	 * @param collection
	 * @param lfns
	 * @return <code>true</code> if anything was changed
	 */
	public static boolean addToCollection(final LFN collection, final Collection<String> lfns) {
		final TreeSet<LFN> toAdd = new TreeSet<>();

		for (final String fileName : lfns) {
			final LFN l = getLFN(fileName);

			if (l != null)
				toAdd.add(getLFN(fileName));
			else {
				logger.log(Level.WARNING, "Could not get the LFN for '" + fileName + "'");
				return false;
			}
		}

		if (toAdd.size() == 0) {
			logger.log(Level.FINER, "Quick exit");
			return false;
		}

		return addToCollection(collection, toAdd);
	}

	/**
	 * @param collection
	 * @param lfns
	 * @return <code>true</code> if anything was changed
	 */
	public static boolean addToCollection(final LFN collection, final Set<LFN> lfns) {
		if (!collection.exists || !collection.isCollection() || lfns == null || lfns.size() == 0) {
			logger.log(Level.FINER, "Quick exit");
			return false;
		}

		final DBFunctions db = ConfigUtils.getDB("alice_data");

		final Set<String> currentLFNs = collection.listCollection();

		try {
			db.setReadOnly(true);
			
			db.query("SELECT collectionId FROM COLLECTIONS where collGUID=string2binary(?);", false, collection.guid.toString());
			
			db.setReadOnly(false);

			if (!db.moveNext()) {
				logger.log(Level.WARNING, "Didn't find any collectionId for guid " + collection.guid.toString());
				return false;
			}

			final int collectionId = db.geti(1);

			final Set<LFN> toAdd = new LinkedHashSet<>();

			for (final LFN lfn : lfns) {
				if (currentLFNs.contains(lfn.getCanonicalName()))
					continue;

				toAdd.add(lfn);
			}

			if (toAdd.size() == 0) {
				logger.log(Level.INFO, "Nothing to add to " + collection.getCanonicalName() + ", all " + lfns.size() + " entries are listed already");
				return false;
			}

			final GUID guid = GUIDUtils.getGUID(collection);

			Set<Integer> commonSEs = guid.size == 0 && guid.seStringList.size() == 0 ? null : new HashSet<>(guid.seStringList);

			boolean updated = false;

			for (final LFN lfn : toAdd) {
				if (commonSEs == null || commonSEs.size() > 0) {
					final Set<PFN> pfns = lfn.whereisReal();

					final Set<Integer> ses = new HashSet<>();

					for (final PFN pfn : pfns)
						ses.add(Integer.valueOf(pfn.seNumber));

					if (commonSEs != null)
						commonSEs.retainAll(ses);
					else
						commonSEs = ses;
				}

				if (db.query("INSERT INTO COLLECTIONS_ELEM (collectionId,origLFN,guid) VALUES (" + collectionId + ", '" + Format.escSQL(lfn.getCanonicalName()) + "', string2binary('"
						+ lfn.guid.toString() + "'));")) {
					guid.size += lfn.size;
					updated = true;
				}
			}

			if (!updated) {
				logger.log(Level.FINER, "No change to the collection");
				return false; // nothing changed
			}

			if (commonSEs != null)
				guid.seStringList = commonSEs;

			collection.size = guid.size;

			collection.ctime = guid.ctime = new Date();

			guid.update();
			collection.update();
		} finally {
			db.close();
		}

		return true;
	}

}
