package alien.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import lazyj.DBFunctions;
import lazyj.cache.ExpirationCache;
import alien.catalogue.GUID;
import alien.catalogue.LFN;
import alien.catalogue.LFNUtils;
import alien.catalogue.PFN;
import alien.config.ConfigUtils;
import alien.io.protocols.Factory;
import alien.io.protocols.Protocol;
import alien.monitoring.Monitor;
import alien.monitoring.MonitorFactory;
import alien.se.SE;
import alien.se.SEUtils;

/**
 * @author costing
 * 
 */
public final class TransferUtils {

	/**
	 * Logger
	 */
	static transient final Logger logger = ConfigUtils.getLogger(TransferUtils.class.getCanonicalName());

	/**
	 * Monitoring component
	 */
	static transient final Monitor monitor = MonitorFactory.getMonitor(TransferUtils.class.getCanonicalName());

	/**
	 * @return database connection to the transfers
	 */
	static DBFunctions getDB() {
		return ConfigUtils.getDB("transfers");
	}

	/**
	 * @param id
	 * @return the transfer with the given ID
	 */
	public static TransferDetails getTransfer(final int id) {
		try (DBFunctions db = getDB()) {
			if (db == null)
				return null;

			if (monitor != null) {
				monitor.incrementCounter("TRANSFERS_db_lookup");
				monitor.incrementCounter("TRANSFERS_get_by_id");
			}

			db.setReadOnly(true);

			db.query("SELECT * FROM TRANSFERS_DIRECT WHERE transferId=?;", false, Integer.valueOf(id));

			if (!db.moveNext())
				return null;

			return new TransferDetails(db);
		}
	}

	/**
	 * @param targetSE
	 * @return transfers to this SE
	 */
	public static List<TransferDetails> getActiveTransfersBySE(final String targetSE) {
		try (DBFunctions db = getDB()) {
			if (db == null)
				return null;

			if (monitor != null) {
				monitor.incrementCounter("TRANSFERS_db_lookup");
				monitor.incrementCounter("TRANSFERS_get_by_destination");
			}

			db.setReadOnly(true);

			db.query("SELECT * FROM TRANSFERS_DIRECT WHERE destination=? ORDER BY transferId", false, targetSE);

			final List<TransferDetails> ret = new ArrayList<>();

			while (db.moveNext())
				ret.add(new TransferDetails(db));

			return ret;
		}
	}

	/**
	 * @return all active transfers
	 */
	public static List<TransferDetails> getAllActiveTransfers(final String targetSE, final String user, final String status, final Integer id, final Integer count, final boolean orderAsc) {
		if (id != null) {
			final List<TransferDetails> l = new ArrayList<>();
			final TransferDetails d = getTransfer(id.intValue());
			if (d != null)
				l.add(d);
			return l;
		}

		try (DBFunctions db = getDB()) {
			if (db == null)
				return null;

			if (monitor != null) {
				monitor.incrementCounter("TRANSFERS_db_lookup");
				monitor.incrementCounter("TRANSFERS_get_by_destination");
			}

			db.setReadOnly(true);

			final String orderClause = (orderAsc ? " ORDER BY transferId ASC" : " ORDER BY transferId DESC");
			final String limitClause = (count != null ? " LIMIT " + count.toString() : "");

			String query = "SELECT * FROM TRANSFERS_DIRECT WHERE " + "(? IS NULL OR user=?) AND (? IS NULL OR destination=?)" + " AND (? IS NULL OR status=?)";
			// db.query("SELECT * FROM TRANSFERS_DIRECT ORDER BY transferId", false);
			query += orderClause + limitClause;
			db.query(query, false, user, user, targetSE, targetSE, status, status);

			final List<TransferDetails> ret = new ArrayList<>();

			while (db.moveNext())
				ret.add(new TransferDetails(db));

			return ret;
		}
	}

	/**
	 * @param username
	 * @return transfers to this SE
	 */
	public static List<TransferDetails> getActiveTransfersByUser(final String username) {
		try (DBFunctions db = getDB()) {
			if (db == null)
				return null;

			db.setReadOnly(true);

			if (monitor != null) {
				monitor.incrementCounter("TRANSFERS_db_lookup");
				monitor.incrementCounter("TRANSFERS_get_by_user");
			}

			String q = "SELECT * FROM TRANSFERS_DIRECT ";

			if (username != null && username.length() > 0)
				q += "WHERE user=? ";

			q += "ORDER BY transferId";

			db.setReadOnly(true);

			if (username != null && username.length() > 0)
				db.query(q, false, username);
			else
				db.query(q);

			final List<TransferDetails> ret = new ArrayList<>();

			while (db.moveNext())
				ret.add(new TransferDetails(db));

			return ret;
		}
	}

	/**
	 * @param l
	 * @param se
	 * @return the transfer ID, <code>0</code> in case the file is already on the target SE, or a negative number in case of problems (-1=wrong parameters, -2=database connection missing, -3=cannot
	 *         locate real pfns -4=the insert query failed, -5=insert query didn't generate a transfer ID. -6=cannot locate the archive LFN to mirror (for a file inside a zip archive))
	 */
	public static int mirror(final LFN l, final SE se) {
		return mirror(l, se, null);
	}

	/**
	 * @param l
	 * @param se
	 * @param onCompletionRemoveReplica
	 *            a move mirror operation, on successful transfer remove the mirror from this SE
	 * @return the transfer ID, <code>0</code> in case the file is already on the target SE, or a negative number in case of problems (-1=wrong parameters, -2=database connection missing, -3=cannot
	 *         locate real pfns -4=the insert query failed, -5=insert query didn't generate a transfer ID. -6=cannot locate the archive LFN to mirror (for a file inside a zip archive))
	 */
	public static int mirror(final LFN l, final SE se, final String onCompletionRemoveReplica) {
		return mirror(l, se, onCompletionRemoveReplica, 3);
	}

	/**
	 * @param l
	 * @param se
	 * @param onCompletionRemoveReplica
	 *            a move mirror operation, on successful transfer remove the mirror from this SE
	 * @param maxAttempts
	 *            maximum number of attempts to copy this file
	 * @return the transfer ID, <code>0</code> in case the file is already on the target SE, or a negative number in case of problems (-1=wrong parameters, -2=database connection missing, -3=cannot
	 *         locate real pfns -4=the insert query failed, -5=insert query didn't generate a transfer ID. -6=cannot locate the archive LFN to mirror (for a file inside a zip archive))
	 */
	public static int mirror(final LFN l, final SE se, final String onCompletionRemoveReplica, final int maxAttempts) {
		if (l == null || !l.exists || !l.isFile() || se == null)
			return -1;

		try (DBFunctions db = getDB()) {
			if (db == null)
				return -2;

			if (monitor != null) {
				monitor.incrementCounter("TRANSFERS_db_lookup");
				monitor.incrementCounter("TRANSFERS_get_by_lfn_and_destination");
			}

			final Set<PFN> pfns = l.whereisReal();

			if (pfns == null)
				return -3;

			for (final PFN p : pfns)
				if (se.equals(p.getSE()))
					return 0;

			if (monitor != null)
				monitor.incrementCounter("TRANSFERS_db_insert");

			final LFN lfnToCopy = LFNUtils.getRealLFN(l);

			if (lfnToCopy == null)
				return -6;

			db.setReadOnly(true);

			db.query(PREVIOUS_TRANSFER_ID_QUERY, false, lfnToCopy.getCanonicalName(), se.seName);

			if (db.moveNext())
				return db.geti(1);

			db.setReadOnly(false);

			db.setLastGeneratedKey(true);

			final Map<String, Object> values = new LinkedHashMap<>();

			values.put("lfn", lfnToCopy.getCanonicalName());
			values.put("destination", se.seName);
			values.put("size", Long.valueOf(lfnToCopy.size));
			values.put("status", "WAITING");
			values.put("sent", Long.valueOf(System.currentTimeMillis() / 1000));
			values.put("received", Long.valueOf(System.currentTimeMillis() / 1000));
			values.put("options", "ur");
			values.put("user", lfnToCopy.owner);
			values.put("type", "mirror");
			values.put("agentid", Integer.valueOf(0));
			values.put("attempts", Integer.valueOf(maxAttempts - 1));

			if (onCompletionRemoveReplica != null && onCompletionRemoveReplica.length() > 0)
				values.put("remove_replica", onCompletionRemoveReplica);

			if (!db.query(DBFunctions.composeInsert("TRANSFERS_DIRECT", values)))
				return -4;

			final Integer i = db.getLastGeneratedKey();

			if (i == null)
				return -5;

			return i.intValue();
		}
	}

	/**
	 * @param guid
	 * @param se
	 * @return the transfer ID, <code>0</code> in case the file is already on the target SE, or a negative number in case of problems (-1=wrong parameters, -2=database connection missing, -3=cannot
	 *         locate real pfns -4=the insert query failed, -5=insert query didn't generate a transfer ID. -6=cannot locate the archive LFN to mirror (for a file inside a zip archive))
	 */
	public static int mirror(final GUID guid, final SE se) {
		return mirror(guid, se, true, null);
	}

	/**
	 * @param guid
	 * @param se
	 * @param onCompletionRemoveReplica
	 *            a move mirror operation, on successful transfer remove the mirror from this SE
	 * @return the transfer ID, <code>0</code> in case the file is already on the target SE, or a negative number in case of problems (-1=wrong parameters, -2=database connection missing, -3=cannot
	 *         locate real pfns -4=the insert query failed, -5=insert query didn't generate a transfer ID. -6=cannot locate the archive LFN to mirror (for a file inside a zip archive))
	 */
	public static int mirror(final GUID guid, final SE se, final String onCompletionRemoveReplica) {
		return mirror(guid, se, true, onCompletionRemoveReplica);
	}

	/**
	 * @param guid
	 * @param se
	 * @param checkPreviousTransfers
	 *            if <code>true</code> then the transfer queue is checked for active transfers identical to the requested one. You should always pass <code>true</code> unless you are sure no such
	 *            transfer could previously exist (either because it was just checked or whatever)
	 * @return the transfer ID, <code>0</code> in case the file is already on the target SE, or a negative number in case of problems (-1=wrong parameters, -2=database connection missing, -3=cannot
	 *         locate real pfns -4=the insert query failed, -5=insert query didn't generate a transfer ID. -6=cannot locate the archive LFN to mirror (for a file inside a zip archive))
	 */
	public static int mirror(final GUID guid, final SE se, final boolean checkPreviousTransfers) {
		return mirror(guid, se, checkPreviousTransfers, null);
	}

	private static final String PREVIOUS_TRANSFER_ID_QUERY = "SELECT transferId FROM TRANSFERS_DIRECT where lfn=? AND destination=? AND status in ('WAITING', 'TRANSFERRING');";

	/**
	 * @param guid
	 * @param se
	 * @param checkPreviousTransfers
	 *            if <code>true</code> then the transfer queue is checked for active transfers identical to the requested one. You should always pass <code>true</code> unless you are sure no such
	 *            transfer could previously exist (either because it was just checked or whatever)
	 * @param onCompletionRemoveReplica
	 *            a move mirror operation, on successful transfer remove the mirror from this SE
	 * @return the transfer ID, <code>0</code> in case the file is already on the target SE, or a negative number in case of problems (-1=wrong parameters, -2=database connection missing, -3=cannot
	 *         locate real pfns -4=the insert query failed, -5=insert query didn't generate a transfer ID. -6=cannot locate the archive LFN to mirror (for a file inside a zip archive))
	 */
	public static int mirror(final GUID guid, final SE se, final boolean checkPreviousTransfers, final String onCompletionRemoveReplica) {
		if (guid == null || !guid.exists() || se == null)
			return -1;

		if (onCompletionRemoveReplica != null && onCompletionRemoveReplica.length() > 0) {
			final SE seRemove = SEUtils.getSE(onCompletionRemoveReplica);

			if (seRemove == null)
				return -1;

			if (!guid.hasReplica(seRemove) || seRemove.equals(se))
				return -1;
		}

		final Set<GUID> realGUIDs = guid.getRealGUIDs();

		final Set<PFN> pfns = new LinkedHashSet<>();

		if (realGUIDs != null && realGUIDs.size() > 0)
			for (final GUID realId : realGUIDs) {
				final Set<PFN> replicas = realId.getPFNs();

				if (replicas == null)
					continue;

				pfns.addAll(replicas);
			}

		if (pfns.size() == 0)
			return -3;

		for (final PFN p : pfns)
			if (se.equals(p.getSE()))
				return 0;

		final String sGUID = guid.guid.toString();

		try (DBFunctions db = getDB()) {
			if (checkPreviousTransfers) {
				db.setReadOnly(true);
				db.query(PREVIOUS_TRANSFER_ID_QUERY, false, sGUID, se.seName);

				if (db.moveNext())
					return db.geti(1);

				db.setReadOnly(false);
			}

			db.setLastGeneratedKey(true);

			final Map<String, Object> values = new LinkedHashMap<>();

			values.put("lfn", sGUID);
			values.put("destination", se.seName);
			values.put("size", Long.valueOf(guid.size));
			values.put("status", "WAITING");
			values.put("sent", Long.valueOf(System.currentTimeMillis() / 1000));
			values.put("received", Long.valueOf(System.currentTimeMillis() / 1000));
			values.put("options", "ur");
			values.put("user", guid.owner);
			values.put("type", "mirror");
			values.put("agentid", Integer.valueOf(0));
			values.put("attempts", Integer.valueOf(0));

			if (onCompletionRemoveReplica != null && onCompletionRemoveReplica.length() > 0)
				values.put("remove_replica", onCompletionRemoveReplica);

			if (!db.query(DBFunctions.composeInsert("TRANSFERS_DIRECT", values)))
				return -4;

			final Integer i = db.getLastGeneratedKey();

			if (i == null)
				return -5;

			return i.intValue();
		}
	}

	public static int removeMirror(final LFN lfn, final SE se) {
		return 0;
	}

	public static final void logAttempt(final Protocol p, final PFN source, final PFN target, final int exitCode, final String failureReason) {
		try (DBFunctions db = getDB()) {
			if (db != null)
				if (ConfigUtils.getConfig().getb("alien.io.TransferUtils.logReason", false))
					db.query("INSERT INTO transfer_attempts (source, destination, protocol, status, reason, etime) VALUES (?, ?, ?, ?, ?, ?);", false, Integer.valueOf(source.seNumber),
							Integer.valueOf(target.seNumber), Byte.valueOf(p.protocolID()), Integer.valueOf(exitCode), failureReason, Long.valueOf(System.currentTimeMillis() / 1000));
				else
					db.query("INSERT INTO transfer_attempts (source, destination, protocol, status, etime) VALUES (?, ?, ?, ?, ?);", false, Integer.valueOf(source.seNumber),
							Integer.valueOf(target.seNumber), Byte.valueOf(p.protocolID()), Integer.valueOf(exitCode), Long.valueOf(System.currentTimeMillis() / 1000));
		}
	}

	private static final class ProtocolStats {
		public int ok = 0;
		public int fail = 0;

		public ProtocolStats() {
			// nothing
		}

		public void update(final DBFunctions db) {
			if (db.getb(2, false))
				ok += db.geti(3);
			else
				fail += db.geti(4);
		}
	}

	private static ExpirationCache<SE, Map<Integer, ProtocolStats>> protocolStatCache = new ExpirationCache<>();

	public static Set<Protocol> filterProtocols(final SE target, final Set<Protocol> protocols) {
		final Set<Protocol> ret = new HashSet<>(protocols.size());

		Map<Integer, ProtocolStats> stats = protocolStatCache.get(target);

		if (stats == null) {
			stats = new HashMap<>();

			try (DBFunctions db = getDB()) {
				db.query("select protocol,status=0,count(1) from transfer_attempts where destination=? and etime>? group by 1,2", false, Integer.valueOf(target.seNumber),
						Long.valueOf(System.currentTimeMillis() / 1000 - 60 * 60 * 6));

				while (db.moveNext()) {
					final Integer protocolID = Integer.valueOf(db.geti(1));

					ProtocolStats pstats = stats.get(protocolID);

					if (pstats == null) {
						pstats = new ProtocolStats();
						stats.put(protocolID, pstats);
					}

					pstats.update(db);
				}
			}

			protocolStatCache.put(target, stats, 1000 * 60 * 5);
		}

		for (final Protocol p : protocols) {
			final ProtocolStats pstats = stats.get(Integer.valueOf(p.protocolID()));

			if (pstats == null || pstats.ok > pstats.fail / 10) {
				ret.add(p);
				continue;
			}
		}

		return ret;
	}

	public static void main(String[] args) {
		Set<Protocol> protocols = new HashSet<>();
		protocols.add(Factory.xrd3cp);
		protocols.add(Factory.xrd3cp4);
		protocols.add(Factory.xrd3cpGW);
		protocols.add(Factory.xrootd);

		System.err.println(filterProtocols(SEUtils.getSE("ALICE::NDGF::DCACHE_TAPE"), protocols));
		System.err.println(filterProtocols(SEUtils.getSE("ALICE::ORNL::EOS"), protocols));
	}
}
