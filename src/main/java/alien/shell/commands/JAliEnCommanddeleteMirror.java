package alien.shell.commands;

import java.util.List;

import alien.catalogue.FileSystemUtils;
import alien.catalogue.GUID;
import alien.catalogue.GUIDUtils;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * @author costing
 *
 */
public class JAliEnCommanddeleteMirror extends JAliEnBaseCommand {
	private boolean useLFNasGuid;
	private String lfn;
	private String se;

	@Override
	public void run() {
		if (this.lfn == null || this.lfn.length() == 0 || this.se == null || this.se.length() == 0) {
			this.printHelp();
			return;
		}
		if (useLFNasGuid) {
			if (!GUIDUtils.isValidGUID(this.lfn)) {
				commander.setReturnCode(1, "This is not a valid GUID");
				return;
			}
			final GUID guid = commander.c_api.getGUID(this.lfn);
			if (guid == null) {
				commander.setReturnCode(2, "No such GUID");
				return;
			}
		}
		else
			lfn = FileSystemUtils.getAbsolutePath(commander.user.getName(), commander.getCurrentDirName(), lfn);

		final int result = commander.c_api.deleteMirror(lfn, this.useLFNasGuid, se);
		if (result == 0)
			commander.printOutln("Mirror scheduled to be deleted from " + this.se);
		else {
			String errline = null;
			switch (result) {
			case -1:
				errline = "invalid GUID";
				break;
			case -2:
				errline = "failed to get SE";
				break;
			case -3:
				errline = "user not authorized";
				break;
			case -4:
				errline = "unknown error";
				break;
			default:
				errline = "unknown result code " + result;
				break;
			}
			commander.setReturnCode(3, "Error deleting mirror: " + errline);
		}
		// check is PFN
	}

	@Override
	public void printHelp() {
		commander.printOutln();
		commander.printOutln("Removes a replica of a file from the catalogue");
		commander.printOutln("Usage:");
		commander.printOutln("        deleteMirror [-g] <lfn> <se> [<pfn>]");
		commander.printOutln();
		commander.printOutln("Options:");
		commander.printOutln("   -g: the lfn is a guid");
		commander.printOutln();
	}

	@Override
	public boolean canRunWithoutArguments() {
		return false;
	}

	/**
	 * @param commander
	 * @param alArguments
	 * @throws OptionException
	 */
	public JAliEnCommanddeleteMirror(final JAliEnCOMMander commander, final List<String> alArguments) throws OptionException {
		super(commander, alArguments);
		try {
			final OptionParser parser = new OptionParser();
			parser.accepts("g");

			final OptionSet options = parser.parse(alArguments.toArray(new String[] {}));

			final List<String> lfns = optionToString(options.nonOptionArguments());
			if (lfns == null) {
				System.out.println(lfns);
				return;
			}
			final int argLen = lfns.size();
			if (argLen != 2) {
				this.printHelp();
				return;
			}

			this.lfn = lfns.get(0);
			this.se = lfns.get(1);

			useLFNasGuid = options.has("g");
		} catch (final OptionException e) {
			printHelp();
			throw e;
		}

	}
}
