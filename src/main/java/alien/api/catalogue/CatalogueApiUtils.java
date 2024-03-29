package alien.api.catalogue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import alien.api.Dispatcher;
import alien.api.ServerException;
import alien.catalogue.CatalogEntity;
import alien.catalogue.FileSystemUtils;
import alien.catalogue.GUID;
import alien.catalogue.LFN;
import alien.catalogue.LFN_CSD;
import alien.catalogue.PFN;
import alien.catalogue.Package;
import alien.catalogue.BookingTable.BOOKING_STATE;
import alien.catalogue.access.AccessType;
import alien.config.ConfigUtils;
import alien.io.TransferDetails;
import alien.se.SE;
import alien.shell.commands.JAliEnCOMMander;
import alien.shell.commands.JAliEnCommandcp;
import alien.site.OutputEntry;
import alien.user.AliEnPrincipal;

/**
 *
 * @author ron
 * @since Jun 03, 2011
 */
public class CatalogueApiUtils {

	/**
	 * Logger
	 */
	static transient final Logger logger = ConfigUtils.getLogger(CatalogueApiUtils.class.getCanonicalName());

	private final JAliEnCOMMander commander;

	/**
	 * @param commander
	 */
	public CatalogueApiUtils(final JAliEnCOMMander commander) {
		this.commander = commander;
	}

	/**
	 * Get LFN from String, only if it exists
	 *
	 * @param slfn
	 *            name of the LFN
	 * @return the LFN object
	 */
	public LFN getLFN(final String slfn) {
		return getLFN(slfn, false);
	}

	/**
	 * Get LFN from String
	 *
	 * @param slfn
	 * @param evenIfDoesntExist
	 * @return the LFN object, that might exist or not (if <code>evenIfDoesntExist = true</code>)
	 */
	public LFN getLFN(final String slfn, final boolean evenIfDoesntExist) {
		final Collection<LFN> ret = getLFNs(Arrays.asList(slfn), false, evenIfDoesntExist);
		return ret != null && ret.size() > 0 ? ret.iterator().next() : null;
	}

	/**
	 * Get LFNs from String as a directory listing, only if it exists
	 *
	 * @param slfn
	 *            name of the LFN
	 * @return the LFN objects
	 */
	public List<LFN> getLFNs(final String slfn) {
		try {
			return Dispatcher.execute(new LFNListingfromString(commander.getUser(), slfn)).getLFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get LFN: " + slfn);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Get LFN from String
	 *
	 * @param slfn
	 *            name of the LFN
	 * @param ignoreFolders
	 * @param evenIfDoesntExist
	 * @return the LFN object
	 */
	public List<LFN> getLFNs(final Collection<String> slfn, final boolean ignoreFolders, final boolean evenIfDoesntExist) {
		try {
			return Dispatcher.execute(new LFNfromString(commander.getUser(), ignoreFolders, evenIfDoesntExist, slfn)).getLFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get LFN: " + slfn);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Get the real file to which the given LFN belongs to. It can be the same file if it exists and has a physical replica or a zip archive containing it, if such an archive can be located.
	 *
	 * @param slfn
	 *            name of the LFN
	 * @return an LFN with physical backing containing the given file, if such an entry can be found, <code>null</code> if not
	 */
	public LFN getRealLFN(final String slfn) {
		try {
			return Dispatcher.execute(new RealLFN(commander.getUser(), slfn)).getRealLFN();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get LFN: " + slfn);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Remove a LFN in the Catalogue
	 *
	 * @param path
	 *            absolute path to the LFN
	 * @return state of the LFN's deletion <code>null</code>
	 */
	public boolean removeLFN(final String path) {
		try {
			return Dispatcher.execute(new RemoveLFNfromString(commander.getUser(), path, false)).wasRemoved();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not remove the LFN: " + path);
			e.getCause().printStackTrace();
		}

		return false;
	}

	/**
	 * Remove a LFN in the Catalogue
	 *
	 * @param path
	 *            absolute path to the LFN
	 * @param recursive
	 *            <code>true</code> to delete directory's content recursively
	 * @return state of the LFN's deletion <code>null</code>
	 */
	public boolean removeLFN(final String path, final boolean recursive) {
		try {
			return Dispatcher.execute(new RemoveLFNfromString(commander.getUser(), path, recursive)).wasRemoved();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not remove the LFN: " + path);
			e.getCause().printStackTrace();
		}

		return false;
	}

	/**
	 * Remove a LFN in the Catalogue
	 *
	 * @param path
	 *            absolute path to the LFN
	 * @param recursive
	 *            <code>true</code> to delete directory's content recursively
	 * @param purge
	 *            <code>true</code> to delete a physical copy
	 * @return state of the LFN's deletion <code>null</code>
	 */
	public boolean removeLFN(final String path, final boolean recursive, final boolean purge) {
		try {
			return Dispatcher.execute(new RemoveLFNfromString(commander.getUser(), path, recursive, purge)).wasRemoved();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not remove the LFN: " + path);
			e.getCause().printStackTrace();
		}

		return false;
	}

	/**
	 * Move a LFN in the Catalogue
	 *
	 * @param path
	 *            absolute path to the LFN
	 * @param newpath
	 *            absolute path to the target
	 * @return state of the LFN's deletion <code>null</code>
	 */
	public LFN moveLFN(final String path, final String newpath) {
		try {
			return Dispatcher.execute(new MoveLFNfromString(commander.getUser(), path, newpath)).newLFN();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not move the LFN-->newLFN: " + path + "-->" + newpath);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Get GUID from String
	 *
	 * @param sguid
	 *            GUID as String
	 * @return the GUID object
	 */
	public GUID getGUID(final String sguid) {
		return getGUID(sguid, false, false);
	}

	/**
	 * Get GUID from String
	 *
	 * @param sguid
	 *            GUID as String
	 * @param evenIfDoesNotExist
	 * @param resolveLFNs
	 *            populate the LFN cache of the GUID object
	 * @return the GUID object
	 */
	public GUID getGUID(final String sguid, final boolean evenIfDoesNotExist, final boolean resolveLFNs) {
		try {
			return Dispatcher.execute(new GUIDfromString(commander.getUser(), sguid, evenIfDoesNotExist, resolveLFNs)).getGUID();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get GUID: " + sguid);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Get PFNs from GUID as String
	 *
	 * @param sguid
	 *            GUID as String
	 * @return the PFNs
	 */
	public Set<PFN> getPFNs(final String sguid) {
		try {
			return Dispatcher.execute(new PFNfromString(commander.getUser(), sguid)).getPFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get GUID: " + sguid);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Get PFNs for reading by LFN
	 *
	 * @param entity
	 *            LFN or GUID to get access to
	 * @param ses
	 *            SEs to prioritize to read from
	 * @param exses
	 *            SEs to deprioritize to read from
	 * @return PFNs, filled with read envelopes and credentials if necessary and authorized
	 */
	public List<PFN> getPFNsToRead(final CatalogEntity entity, final List<String> ses, final List<String> exses) {
		try {
			return Dispatcher.execute(new PFNforReadOrDel(commander.getUser(), commander.getSite(), AccessType.READ, entity, ses, exses)).getPFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get PFN for: " + entity);
			e.getCause().printStackTrace();

		}
		return null;
	}

	/**
	 * Get PFNs for writing by LFN
	 *
	 * @param lfn
	 *            LFN of the entry as String
	 * @param guid
	 * @param ses
	 *            SEs to prioritize to read from
	 * @param exses
	 *            SEs to de-prioritize
	 * @param qos
	 *            QoS types and counts to ask for
	 * @return PFNs, filled with write envelopes and credentials if necessary and authorized
	 */
	public List<PFN> getPFNsToWrite(final LFN lfn, final GUID guid, final List<String> ses, final List<String> exses, final HashMap<String, Integer> qos) {
		try {
			return Dispatcher.execute(new PFNforWrite(commander.getUser(), commander.getSite(), lfn, guid, ses, exses, qos)).getPFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get PFN for: " + lfn);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Register PFNs with envelopes
	 *
	 * @param envelopes
	 * @return PFNs that were successfully registered
	 * @param state the state to register the files in
	 */
	public List<PFN> registerEnvelopes(final List<String> envelopes, final BOOKING_STATE state) {
		try {
			final List<String> encryptedEnvelopes = new LinkedList<>();
			final List<String> signedEnvelopes = new LinkedList<>();

			for (final String envelope : envelopes)
				if (envelope.contains("&signature="))
					signedEnvelopes.add(envelope);
				else
					encryptedEnvelopes.add(envelope);

			final List<PFN> ret = new LinkedList<>();

			if (signedEnvelopes.size() > 0) {
				final List<PFN> signedPFNs = Dispatcher.execute(new RegisterEnvelopes(commander.getUser(), envelopes, state)).getPFNs();

				if (signedPFNs != null && signedPFNs.size() > 0)
					ret.addAll(signedPFNs);
			}

			for (final String envelope : encryptedEnvelopes) {
				final List<PFN> encryptedPFNs = Dispatcher.execute(new RegisterEnvelopes(commander.getUser(), envelope, 0, null, state)).getPFNs();

				if (encryptedPFNs != null && encryptedPFNs.size() > 0)
					ret.addAll(encryptedPFNs);
			}

			return ret;
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get PFNs for: " + envelopes.toString());
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Register PFNs with envelopes
	 *
	 * @param encryptedEnvelope
	 * @param size
	 * @param md5
	 * @param state what to do with the given files
	 * @return PFNs that were successfully registered
	 */
	public List<PFN> registerEncryptedEnvelope(final String encryptedEnvelope, final int size, final String md5, final BOOKING_STATE state) {
		try {
			return Dispatcher.execute(new RegisterEnvelopes(commander.getUser(), encryptedEnvelope, size, md5, state)).getPFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get PFNs for: " + encryptedEnvelope);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Create a directory in the Catalogue
	 *
	 * @param path
	 * @return LFN of the created directory, if successful, else <code>null</code>
	 */
	public LFN createCatalogueDirectory(final String path) {
		return createCatalogueDirectory(path, false);
	}

	/**
	 *
	 * @param path
	 * @return LFN of the created file, if successful, else <code>null</code>
	 */
	public LFN touchLFN(final String path) {
		try {
			return Dispatcher.execute(new TouchLFNfromString(commander.getUser(), path)).getLFN();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not create the file: " + path);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Create a directory in the Catalogue
	 *
	 * @param path
	 * @param createNonExistentParents
	 * @return LFN of the created directory, if successful, else <code>null</code>
	 */
	public LFN createCatalogueDirectory(final String path, final boolean createNonExistentParents) {
		try {
			return Dispatcher.execute(new CreateCatDirfromString(commander.getUser(), path, createNonExistentParents)).getDir();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not create the CatDir: " + path);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Remove a directory in the Catalogue
	 *
	 * @param path
	 * @return state of directory's deletion <code>null</code>
	 */
	public boolean removeCatalogueDirectory(final String path) {
		try {
			return Dispatcher.execute(new RemoveCatDirfromString(commander.getUser(), path)).wasRemoved();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not remove the CatDir: " + path);
			e.getCause().printStackTrace();
		}

		return false;
	}

	/**
	 * Find an LFN based on pattern
	 *
	 * @param path
	 * @param pattern
	 * @param flags
	 * @return result LFNs
	 */
	public Collection<LFN> find(final String path, final String pattern, final int flags) {
		return find(path, pattern, flags, "");
	}

	/**
	 * Find an LFN based on pattern and save to XmlCollection
	 * 
	 * @param path
	 * @param pattern
	 * @param flags
	 * @param xmlCollectionName
	 * @return result LFNs
	 */
	public Collection<LFN> find(final String path, final String pattern, final int flags, final String xmlCollectionName) {
		return this.find(path, pattern, flags, xmlCollectionName, Long.valueOf(0));
	}

	/**
	 * Find an LFN based on pattern and save to XmlCollection
	 * 
	 * @param path
	 * @param pattern
	 * @param flags
	 * @param xmlCollectionName
	 * @param queueid
	 * @return result LFNs
	 */
	public Collection<LFN> find(final String path, final String pattern, final int flags, final String xmlCollectionName, Long queueid) {
		return this.find(path, pattern, null, flags, xmlCollectionName, queueid);
	}

	/**
	 * Find an LFN based on pattern and save to XmlCollection
	 * 
	 * @param path
	 * @param pattern
	 * @param query
	 * @param flags
	 * @param xmlCollectionName
	 * @param queueid
	 * @return result LFNs
	 */
	public Collection<LFN> find(final String path, final String pattern, final String query, final int flags, final String xmlCollectionName, Long queueid) {
		try {
			return Dispatcher.execute(new FindfromString(commander.getUser(), path, pattern, query, flags, xmlCollectionName, queueid)).getLFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Unable to execute find: path (" + path + "), pattern (" + pattern + "), flags (" + flags + ")");
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Get an SE by its name
	 *
	 * @param se
	 *            name of the SE
	 * @return SE object
	 */
	public SE getSE(final String se) {
		try {
			return Dispatcher.execute(new SEfromString(commander.getUser(), se)).getSE();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get SE: " + se);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Get an SE by its number
	 *
	 * @param seno
	 *            number of the SE
	 * @return SE object
	 */
	public SE getSE(final int seno) {
		try {
			return Dispatcher.execute(new SEfromString(commander.getUser(), seno)).getSE();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get SE: " + seno);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Get the SEs matching the given set of names. The argument can be null or empty, in which case all defined SEs are returned. Invalid SEs are silently skipped, so the returned list could have a
	 * different size (and order) than the given argument.
	 * 
	 * @param ses
	 * @return the SEs matching the request
	 */
	public List<SE> getSEs(final List<String> ses) {
		try {
			return Dispatcher.execute(new ListSEs(commander.getUser(), ses)).getSEs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not list SEs: " + ses);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Get Packages for a certain platform
	 *
	 * @param platform
	 * @return the Packages
	 */
	public List<Package> getPackages(final String platform) {
		try {
			return Dispatcher.execute(new PackagesfromString(commander.getUser(), platform)).getPackages();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get Packages for: " + platform);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * @param lfn_name
	 * @param username_to_chown
	 * @param groupname_to_chown
	 * @param recursive
	 * @return command result for each lfn
	 */
	public HashMap<String, Boolean> chownLFN(final String lfn_name, final String username_to_chown, final String groupname_to_chown, final boolean recursive) {
		if (lfn_name == null || lfn_name.length() == 0)
			return null;

		final LFN lfn = this.getLFN(lfn_name);

		if (lfn == null)
			return null;
		try {
			final ChownLFN cl = Dispatcher.execute(new ChownLFN(commander.getUser(), lfn_name, username_to_chown, groupname_to_chown, recursive));
			if (cl != null)
				return cl.getResults();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not chown " + lfn_name + " for " + username_to_chown);
			e.getCause().printStackTrace();
		}
		return null;
	}

	/**
	 * @param lfn_name
	 * @param ses
	 * @param exses
	 * @param qos
	 * @param useLFNasGuid
	 * @param attempts
	 * @return command result for each lfn
	 */
	public HashMap<String, Long> mirrorLFN(final String lfn_name, final List<String> ses, final List<String> exses, final HashMap<String, Integer> qos, final boolean useLFNasGuid,
			final Integer attempts) {

		if (lfn_name == null || lfn_name.length() == 0)
			throw new IllegalArgumentException("Empty LFN name");

		try {
			final MirrorLFN ml = Dispatcher.execute(new MirrorLFN(commander.getUser(), lfn_name, ses, exses, qos, useLFNasGuid, attempts));
			return ml.getResultHashMap();
		}
		catch (final SecurityException e) {
			logger.log(Level.WARNING, e.getMessage());
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, e.getMessage());
			e.getCause().printStackTrace();
		}
		return null;
	}

	/**
	 * @param site
	 * @param write
	 * @param lfn
	 * @param qos
	 * @return SE distance list
	 */
	public List<HashMap<SE, Double>> listSEDistance(final String site, final boolean write, final String lfn, final String qos) {
		ListSEDistance lsd;
		try {
			lsd = Dispatcher.execute(new ListSEDistance(commander.getUser(), site, write, lfn, qos));
			return (lsd != null ? lsd.getSEDistances() : null);
		}
		catch (final ServerException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * @param status
	 * @param toSE
	 * @param user
	 * @param id
	 * @param count
	 * @param desc
	 * @return transfer details
	 */
	public List<TransferDetails> listTransfer(final String status, final String toSE, final String user, final Long id, final int count, final boolean desc) {

		ListTransfer lt;
		try {
			lt = Dispatcher.execute(new ListTransfer(commander.getUser(), status, toSE, user, id, count, desc));
			return (lt != null ? lt.getTransfers() : null);
		}
		catch (final ServerException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * @param file
	 * @param isGuid
	 * @param se
	 * @return exit code
	 */
	public int deleteMirror(final String file, final boolean isGuid, final String se) {
		try {
			final DeleteMirror dm = Dispatcher.execute(new DeleteMirror(commander.getUser(), file, isGuid, se));
			return dm.getResult();
		}
		catch (final ServerException e) {
			e.printStackTrace();
			return -100;
		}
	}

	/**
	 * @param entry
	 * @param outputDir
	 * @param user
	 */
	public static void registerEntry(final OutputEntry entry, final String outputDir, final AliEnPrincipal user) {
		try {
			Dispatcher.execute(new RegisterEntry(entry, outputDir, user));
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not register entry " + entry.getName());
			e.getCause().printStackTrace();
		}
	}

	/**
	 * Upload a local file to the Grid
	 * 
	 * @param localFile
	 *            full path to local file
	 * @param toLFN
	 *            catalogue entry name
	 * @param args
	 *            other `cp` command parameters to pass
	 * @throws IOException
	 */
	public void uploadFile(final File localFile, final String toLFN, final String... args) throws IOException {
		final LFN l = getLFN(toLFN, true);

		if (l.exists)
			throw new IOException("LFN already exists: " + toLFN);

		final String lfnAbsolutePath = FileSystemUtils.getAbsolutePath(commander.getUser().getName(), commander.getCurrentDirName(), toLFN);

		final ArrayList<String> cpArgs = new ArrayList<>();
		cpArgs.add("file:" + localFile.getAbsolutePath());
		cpArgs.add(lfnAbsolutePath);

		if (args != null)
			for (final String arg : args)
				cpArgs.add(arg);

		final JAliEnCommandcp cp = new JAliEnCommandcp(commander, cpArgs);

		cp.copyLocalToGrid(localFile, lfnAbsolutePath);
	}

	/**
	 * Download a remote file from the Grid
	 * 
	 * @param fromLFN
	 * 
	 * @param localFile
	 *            full path to local file
	 * @param args
	 *            other `cp` command parameters to pass
	 * @throws IOException
	 */
	public void downloadFile(final String fromLFN, final File localFile, final String... args) throws IOException {
		if (localFile.exists())
			throw new IOException("localFile already exists: " + localFile.getAbsolutePath());

		final String lfnAbsolutePath = FileSystemUtils.getAbsolutePath(commander.getUser().getName(), commander.getCurrentDirName(), fromLFN);

		final ArrayList<String> cpArgs = new ArrayList<>();
		cpArgs.add(lfnAbsolutePath);
		cpArgs.add("file:" + localFile.getAbsolutePath());

		if (args != null)
			for (final String arg : args)
				cpArgs.add(arg);

		final JAliEnCommandcp cp = new JAliEnCommandcp(commander, cpArgs);

		cp.copyGridToLocal(lfnAbsolutePath, localFile);
	}

	/**
	 * Find files that are members of this archive
	 * 
	 * @param archive
	 * 
	 * @return list of archive members LFNs
	 */
	public List<LFN> getArchiveMembers(final String archive) {
		try {
			return Dispatcher.execute(new GetArchiveMembers(commander.getUser(), archive)).getArchiveMembers();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not return members for " + archive);
			e.getCause().printStackTrace();
		}
		return null;
	}

	/**
	 * Get LFN_CSDs from String as a listing, only if it exists
	 *
	 * @param slfn
	 *            name of the LFN
	 * @return the LFNCSD objects
	 */
	public Collection<LFN_CSD> getLFNCSDs(final String slfn) {
		try {
			return Dispatcher.execute(new LFNCSDListingfromString(commander.getUser(), slfn)).getLFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get LFN: " + slfn);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Find an LFNCSD based on pattern and save to XmlCollection
	 * 
	 * @param path
	 * @param pattern
	 * @param metadata
	 * @param flags
	 * @param xmlCollectionName
	 * @param queueid
	 * @return result LFNCSDs
	 */
	public Collection<LFN_CSD> find_csd(final String path, final String pattern, final String metadata, final int flags, final String xmlCollectionName, Long queueid) {
		try {
			return Dispatcher.execute(new FindCsdfromString(commander.getUser(), path, pattern, metadata, flags, xmlCollectionName, queueid)).getLFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Unable to execute find: path (" + path + "), pattern (" + pattern + "), flags (" + flags + ")");
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Create a directory in the Cassandra catalogue
	 *
	 * @param path
	 * @param createNonExistentParents
	 * @return LFN_CSD of the created directory, if successful, else <code>null</code>
	 */
	public LFN_CSD createCatalogueDirectoryCsd(final String path, final boolean createNonExistentParents) {
		try {
			return Dispatcher.execute(new CreateCsdCatDirfromString(commander.getUser(), path, createNonExistentParents)).getDir();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not create the CatDir: " + path);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 *
	 * @param path
	 * @return LFNCSD of the created file, if successful, else <code>null</code>
	 */
	public LFN_CSD touchLFNCSD(final String path) {
		try {
			return Dispatcher.execute(new TouchLFNCSDfromString(commander.getUser(), path)).getLFN();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not create the file: " + path);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Get LFN_CSDs from collection
	 *
	 * @param slfn
	 * @return the LFNCSD objects
	 */
	public Collection<LFN_CSD> getLFNCSD(final Collection<String> slfn) {
		return getLFNCSD(slfn, false);
	}

	/**
	 * Get LFN_CSD from String
	 *
	 * @param lfn_path
	 * @return the LFNCSD object
	 */
	public LFN_CSD getLFNCSD(final String lfn_path) {
		ArrayList<String> slfn = new ArrayList<>();
		slfn.add(lfn_path);
		Collection<LFN_CSD> slfn_res = new ArrayList<>();

		slfn_res = getLFNCSD(slfn, false);

		if (slfn_res != null && slfn_res.iterator().hasNext())
			return slfn_res.iterator().next();

		return null;
	}

	/**
	 * Get LFN_CSDs from collection, potentially using UUIDs
	 *
	 * @param slfn
	 * @param lfns_are_uuids
	 * @return the LFNCSD objects
	 */
	public Collection<LFN_CSD> getLFNCSD(final Collection<String> slfn, final boolean lfns_are_uuids) {
		try {
			return Dispatcher.execute(new LFNCSDfromString(commander.getUser(), true, false, lfns_are_uuids, slfn)).getLFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get LFNs: " + slfn.toString());
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * Move a LFNCSD in the catalogue
	 *
	 * @param path
	 *            absolute path to the LFN
	 * @param newpath
	 *            absolute path to the target
	 * @return code of the LFNCSD mv
	 */
	public int moveLFNCSD(final String path, final String newpath) {
		try {
			return Dispatcher.execute(new MoveLFNCSDfromString(commander.getUser(), path, newpath)).getMvCode();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not move the LFN-->newLFN: " + path + "-->" + newpath);
			e.getCause().printStackTrace();
		}

		return -1;
	}

	/**
	 * Get GUID from String
	 *
	 * @param uuid
	 *            UUID as String
	 * @return the LFNCSD object
	 */
	public LFN_CSD guid2lfncsd(final String uuid) {
		try {
			return Dispatcher.execute(new LFNCSDfromUUIDString(commander.getUser(), uuid)).getLFNCSD();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get UUID: " + uuid);
			e.getCause().printStackTrace();
		}

		return null;
	}

	/**
	 * @param lfn_name
	 * @param username_to_chown
	 * @param groupname_to_chown
	 * @param recursive
	 * @return command result for each lfncsd
	 */
	public boolean chownLFNCSD(final String lfn_name, final String username_to_chown, final String groupname_to_chown, final boolean recursive) {
		if (lfn_name == null || lfn_name.length() == 0)
			return false;

		// final LFN_CSD lfn = this.getLFNCSD(lfn_name);
		//
		// if (lfn == null || !lfn.exists)
		// return false;
		try {
			final ChownLFNCSD cl = Dispatcher.execute(new ChownLFNCSD(commander.getUser(), lfn_name, username_to_chown, groupname_to_chown, recursive));
			if (cl != null)
				return cl.getSuccess();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not chown " + lfn_name + " for " + username_to_chown);
			e.getCause().printStackTrace();
		}
		return false;
	}

	/**
	 * Get PFNs for reading by LFNCSD
	 * 
	 *
	 * @param entity
	 *            LFN or GUID to get access to
	 * @param ses
	 *            SEs to prioritize to read from
	 * @param exses
	 *            SEs to deprioritize to read from
	 * @return PFNs, filled with read envelopes and credentials if necessary and authorized
	 */
	public List<PFN> getPFNsToReadCsd(final CatalogEntity entity, final List<String> ses, final List<String> exses) {
		try {
			return Dispatcher.execute(new PFNforReadOrDelCsd(commander.getUser(), commander.getSite(), AccessType.READ, entity, ses, exses)).getPFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get PFN for: " + entity);
			e.getCause().printStackTrace();

		}
		return null;
	}

	/**
	 * Register the output of a given job ID
	 * 
	 * @param jobId job ID to register the output of
	 * @return the list of booked (so written but not yet committed) LFNs for this (normally failed) job ID
	 */
	public Collection<LFN> registerOutput(final long jobId) {
		try {
			return Dispatcher.execute(new RegisterOutput(commander.getUser(), jobId)).getRegisteredLFNs();
		}
		catch (final ServerException e) {
			logger.log(Level.WARNING, "Could not get booked LFNs for job ID " + jobId);
			e.getCause().printStackTrace();
		}
		return null;
	}
}
