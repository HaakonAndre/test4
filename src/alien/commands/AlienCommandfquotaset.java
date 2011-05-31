package alien.commands;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import lazyj.Log;
import alien.catalogue.FileSystemUtils;
import alien.catalogue.LFN;
import alien.catalogue.LFNUtils;
import alien.quotas.Quota;
import alien.quotas.QuotaUtilities;
import alien.user.AliEnPrincipal;

/**
 * @author Steffen Schreiner
 * @since May 30, 2011 implements AliEn fquota list command
 * */
public class AlienCommandfquotaset extends AlienAdminCommand {
	/**
	 * ls command arguments : -help/l/a
	 */
	private static ArrayList<String> lsArguments = new ArrayList<String>();

	static {
	}

	/**
	 * marker for -help argument
	 */
	private boolean bHelp = false;

	/**
	 * marker for -l argument
	 */
	private String setWhat = null;
	
	/**
	 * marker for -l argument
	 */
	private long setTo = 0;
	
	/**
	 * marker for -a argument
	 */
	private String user = null;

	/**
	 * marker for -g argument
	 */
	private final static String Iam = "fquota set";

	/**
	 * @param p
	 *            AliEn principal received from https request
	 * @param al
	 *            all arguments received from SOAP request, contains user,
	 *            current directory and command
	 * @throws Exception
	 */
	public AlienCommandfquotaset(final AliEnPrincipal p,
			final ArrayList<Object> al) throws Exception {
		super(p, al);
	}

	/**
	 * @param p
	 *            AliEn principal received from https request
	 * @param sUsername
	 *            username received from SOAP request, can be different than the
	 *            one from the https request is the user make a su
	 * @param sCurrentDirectory
	 *            the directory from the user issued the command
	 * @param sCommand
	 *            the command requested through the SOAP request
	 * @param iDebugLevel
	 * @param alArguments
	 *            command arguments, can be size 0 or null
	 * @throws Exception
	 */
	public AlienCommandfquotaset(final AliEnPrincipal p,
			final String sUsername, final String sCurrentDirectory,
			final String sCommand, final int iDebugLevel,
			final List<?> alArguments) throws Exception {
		super(p, sUsername, sCurrentDirectory, sCommand, iDebugLevel,
				alArguments);
	}

	/**
	 * @return a map of <String, List<String>> with only 2 keys
	 *         <ul>
	 *         <li>rcvalues - file list</li>
	 *         <li>rcmessages - file list with an extra \n at the end of the
	 *         file name</li>
	 *         </ul>
	 */
	@Override
	public HashMap<String, ArrayList<String>> executeCommand() {
		HashMap<String, ArrayList<String>> hmReturn = new HashMap<String, ArrayList<String>>();

		ArrayList<String> alrcValues = new ArrayList<String>();
		ArrayList<String> alrcMessages = new ArrayList<String>();

		// we got arguments for fquota list
		if (this.alArguments != null && this.alArguments.size() >= 3) {

			
			ArrayList<String> args = new ArrayList<String>(3);
			
			for (Object oArg : this.alArguments) {
				String sArg = (String) oArg;

				// we got an argument
				if (sArg.startsWith("-")) {
					if (sArg.length() == 1) {
						alrcMessages.add("Expected argument after \"-\" \n "
								+ Iam + " -help for more help\n");
					} else {
						String sLocalArg = sArg.substring(1);

						if (sLocalArg.startsWith("h")) {
							bHelp = true;
							}
						}
				} else {
					args.add(sArg);
				}
				
			}
			if(args.size() == 3){
				user = args.get(0);
				System.out.println("user: " + user);
				setWhat = args.get(1);
				System.out.println("setWhat: " + setWhat);
				System.out.println("args2" + args.get(2));
				setTo = Long.getLong(args.get(2));
			}else
				bHelp=true;
		} else 
			bHelp=true;
		
		if (!bHelp) {

				Quota quota = QuotaUtilities.getFQuota(user);
				
				if(quota==null)
					System.out.println("Couldn't get the quota");
				
				if(setWhat=="maxNbFiles")
					// TODO: set it
					System.out.println("TODO: Set maxNbFiles in quotas");
				else if(setWhat=="maxTotalSize")
					// TODO: set it
					System.out.println("TODO: Set maxTotalSize in quotas");
				else 
					alrcMessages
					.add("Wrong oifield name! Choose one of them: maxNbFiles, maxTotalSize");

		} else {

			alrcMessages.add(AlienTime.getStamp()
					+ "Usage: \nfquota set  <username> <field> <value> - set the user quota\n");
			alrcMessages.add("		(maxNbFiles, maxTotalSize(Byte))\n");
			alrcMessages.add("use <user>=% for all users\n");
		
				
		}

		hmReturn.put("rcvalues", alrcValues);
		hmReturn.put("rcmessages", alrcMessages);

		return hmReturn;
	}

	private static final DateFormat formatter = new SimpleDateFormat(
			"MMM dd HH:mm");

	private static synchronized String format(final Date d) {
		return formatter.format(d);
	}

}
