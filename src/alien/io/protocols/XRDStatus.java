package alien.io.protocols;

/**
 * Wrapper around the output of an 
 * 
 * @author costing
 */
public class XRDStatus {

	/**
	 * Whether or not the command was successfully executed
	 */
	public final boolean commandOK;
	
	/**
	 * Output/error message from the command
	 */
	public final String commandOutput;
	
	/**
	 * @param commandOK
	 * @param commandOutput 
	 */
	public XRDStatus(final boolean commandOK, final String commandOutput){
		this.commandOK = commandOK;
		this.commandOutput = commandOutput;
	}
	
	@Override
	public String toString() {
		if (commandOK)
			return "OK";
		
		return "ERR: "+commandOutput;
	}
	
}
