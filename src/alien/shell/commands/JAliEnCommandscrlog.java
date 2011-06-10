package alien.shell.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import alien.perl.commands.AlienTime;

/**
 * @author ron
 * @since June 8, 2011
 */
public class JAliEnCommandscrlog extends JAliEnBaseCommand {

	/**
	 * marker for -c argument
	 */
	private boolean bC = false;

	/**
	 * the HashMap for the log screens
	 */
	private static HashMap<Integer, List<String>> scrlogs = new HashMap<Integer, List<String>>(
			10);

	/**
	 * execute the sclog
	 */
	public void execute() {

		int logno = 0;

		for (String arg : alArguments) {
			if ("-c".equals(arg))
				bC = true;
			else
				try {
					logno = Integer.parseInt(arg);
				} catch (NumberFormatException n) {
				}
		}

		if (logno > 9)
			printHelp();
		else if (bC)
			scrlogs.put(logno, new ArrayList<String>());
		else if (scrlogs.get(logno) != null){
			System.out.println(":"+logno+" [screenlog pasting]");
			for (String logline : scrlogs.get(logno)) {
				System.out.println(logline);
			}
		}else
			System.out.println(":"+logno+" [screenlog is empty]");

	}

	/**
	 * get the directory listing of the ls
	 * 
	 * @return list of the LFNs
	 */
	protected static void addScreenLogLine(int logno, String line) {
		if (scrlogs.get(logno) == null)
			scrlogs.put(logno, new ArrayList<String>());
//		ArrayList<String> buf = (ArrayList<String>) scrlogs.get(logno);
//		buf.add(line);
//		scrlogs.put(logno,buf);
		scrlogs.get(logno).add(line);
	}

	/**
	 * printout the help info
	 */
	public void printHelp() {
		System.out.println(AlienTime.getStamp() + "Usage: scrlog [-c] <no>");
		System.out
				.println("You have 0-9 log screens, that you can fill and display");
		System.out
				.println("call '<command> &<no>' to log <command> to screen <no> in background");
		System.out.println("default '<command> &' will go to numer 0");
		System.out.println("scrlog <no> to display the log");
		System.out.println("scrlog -c <no> to clear the log");
		System.out.println("scrlog -c <no> will clear log number 0");
	}

	/**
	 * ls can run without arguments
	 * 
	 * @return <code>true</code>
	 */
	public boolean canRunWithoutArguments() {
		return true;
	}

	/**
	 * nonimplemented command's silence trigger, scrlog is never silent
	 */
	public void silent() {
	}

	/**
	 * Constructor needed for the command factory in JAliEnCOMMander
	 * 
	 * @param alArguments
	 *            the arguments of the command
	 */
	public JAliEnCommandscrlog(final ArrayList<String> alArguments) {
		super(alArguments);
	}

}