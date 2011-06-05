package alien.perl.commands;

import java.security.Principal;
import java.util.ArrayList;

import lazyj.Log;
import alien.user.AliEnPrincipal;

/**
 * @author Alina Grigoras
 * Alien commands factory
 */
public class AlienCommands {
	/**
	 * Returns a implementation of the command
	 * @param p Alien principal received from request
	 * @param al array of arguments received from the request
	 * @return AlienCommand implementation for the requested command
	 * @throws Exception
	 */
	public static AlienCommand getAlienCommand(AliEnPrincipal p, ArrayList<Object> al) throws Exception{
		if(p == null)
			throw new SecurityException("No Alien Principal! We hane no credentials");
		
		if(al.size() < 3){
			throw new Exception("Alien Command did not receive minimum number of arguments (in this order): username, current directory, command (+ arguments)? ");
		}
		
		
		String sLocalCommand = (String) al.get(2);
		
		Log.log(Log.INFO, "Command received = \""+sLocalCommand+"\"");
		

		try{
		if("ls".equals(sLocalCommand)){	
			return new AlienCommandls(p, al);
		}
		else if("authorize".equals(sLocalCommand)){
			return new AlienCommandauthorize(p, al);
		}
		else if("whereis".equals(sLocalCommand)){
			return new AlienCommandwhereis(p, al);
		}
		else if("tabCompletion".equals(sLocalCommand)){
			return new AlienCommandCompletePath(p, al);
		}
		else if("lfn2guid".equals(sLocalCommand)){
			return new AlienCommandlfn2guid(p, al);
		}
		else if("fquota_list".equals(sLocalCommand)){
			return new AlienCommandfquotalist(p, al);
		}
		else if("fquota_set".equals(sLocalCommand)){
			return new AlienCommandfquotaset(p, al);
		}
		else return null;
		} 
		catch(PerlSecurityException se){
			AlienCommandError er = new AlienCommandError(p,al);
			er.errorMessage = se.getMessage();
			return er;
		}
	}

	/**
	 * @param p AliEn principal received from the https request
	 * @param al array containg the user that issued the command, the directory from where the command was issued, the command and its arguments 
	 * @return the name of the requested command
	 * @throws Exception
	 */
	public static String getAlienCommandString(Principal p, ArrayList<Object> al) throws Exception {
		if(p == null)
			throw new SecurityException("No Alien Principal! We hane no credentials");
		
		if(al.size() < 3){
			throw new Exception("Alien Command did not receive minimum number of arguments (in this order): username, current directory, command (+ arguments)? ");
		}
		
		String sLocalCommand = (String) al.get(2);
		Log.log(Log.INFO, "Command received = "+sLocalCommand);
		
		return sLocalCommand;
	}
}