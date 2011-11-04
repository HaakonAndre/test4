package alien.shell.commands;

import java.util.ArrayList;

import joptsimple.OptionException;
import alien.catalogue.GUID;

/**
 * @author ron
 * @since June 4, 2011
 */
public class JAliEnCommandguid2lfn extends JAliEnBaseCommand {

	/**
	 * entry the call is executed on, either representing a LFN 
	 */
	private String guidName = null;

	/**
	 * execute the lfn2guid
	 */
	@Override
	public void run() {

			GUID guid = commander.c_api.getGUID(guidName);
			
			if(guid==null)
				out.printErrln("Could not get the GUID [" + guidName + "].");
			else{
				// TODO: DOES NOT WORK! we don't get the LFNs
				if(guid.getLFNs()!=null && guid.getLFNs().iterator().hasNext())
					out.printOutln(padRight(guid.guid+"", 40) + guid.getLFNs().iterator().next());
				else
					out.printErrln("Could not get the GUID for [" + guid.guid + "].");
			}
	
	}

	/**
	 * printout the help info
	 */
	@Override
	public void printHelp() {
		
		out.printOutln();
		out.printOutln(helpUsage("guid2lfn","<GUID>"));
		out.printOutln();
	}

	/**
	 * guid2lfn cannot run without arguments
	 * 
	 * @return <code>false</code>
	 */
	@Override
	public boolean canRunWithoutArguments() {
		return false;
	}

	/**
	 * Constructor needed for the command factory in JAliEnCOMMander
	 * @param commander 
	 * @param out 
	 * 
	 * @param alArguments
	 *            the arguments of the command
	 * @throws OptionException 
	 */
	public JAliEnCommandguid2lfn(JAliEnCOMMander commander, UIPrintWriter out,
			final ArrayList<String> alArguments) throws OptionException {
		super(commander, out,alArguments);
		
		if(alArguments.size()!=1)
			throw new JAliEnCommandException();
		
		guidName = alArguments.get(0);
		
		
	}

}