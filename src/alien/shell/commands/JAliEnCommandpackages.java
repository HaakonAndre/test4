package alien.shell.commands;

import java.util.ArrayList;
import java.util.List;

import alien.catalogue.Package;
import alien.user.UsersHelper;

/**
 * @author ron
 * @since Nov 23, 2011
 */
public class JAliEnCommandpackages extends JAliEnBaseCommand {
	
	@Override
	public void run() {

		
		String platform = "Linux-x86_64";
		
		List<Package> packs = commander.c_api.getPackages(platform);
				
		
		if (packs != null){
			for (Package p: packs){
				
				if(out.isRootPrinter())
					out.setReturnArgs(deserializeForRoot());
				else
					if(!isSilent())
						out.printOutln("	" + p.getFullName());
			}
		}
		else{
			out.printErrln("Couldn't find any packages.");
			out.setReturnArgs(deserializeForRoot(0));
		}

	}

	/**
	 * printout the help info, none for this command
	 */
	@Override
	public void printHelp() {
		
		out.printOutln();
		out.printOutln(helpUsage("packages","  list available packages"));
		out.printOutln();
	}

	/**
	 * cd can run without arguments 
	 * @return <code>true</code>
	 */
	@Override
	public boolean canRunWithoutArguments() {
		return true;
	}

	
	/**
	 * Constructor needed for the command factory in commander
	 * @param commander 
	 * @param out 
	 * 
	 * @param alArguments
	 *            the arguments of the command
	 */
	public JAliEnCommandpackages(JAliEnCOMMander commander, UIPrintWriter out, final ArrayList<String> alArguments){
		super(commander, out,alArguments);
	}
}