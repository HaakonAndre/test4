package alien.shell.commands;

import java.util.List;

import joptsimple.OptionException;

/**
 *
 */
public class JAliEnCommanddf extends JAliEnBaseCommand {

	@Override
	public void run() {
		commander.setReturnCode(1, "not implemented yet");
	}

	@Override
	public void printHelp() {
		commander.printOutln("Shows free disk space");
		commander.printOutln("Usage: df");
		commander.printOutln();
	}

	@Override
	public boolean canRunWithoutArguments() {
		return true;
	}

	/**
	 * @param commander
	 * @param alArguments
	 * @throws OptionException
	 */
	public JAliEnCommanddf(final JAliEnCOMMander commander, final List<String> alArguments) throws OptionException {
		super(commander, alArguments);
	}
}
