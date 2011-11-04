package alien.api.catalogue;

import alien.api.Request;
import alien.catalogue.LFN;
import alien.catalogue.LFNUtils;
import alien.user.AliEnPrincipal;

/**
 * 
 * @author ron
 * @since Oct 27, 2011
 */
public class RemoveCatDirfromString extends Request {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5481660419374953794L;
	
	private final String path;
	
	private boolean wasRemoved = false;

	/**
	 * @param user 
	 * @param role 
	 * @param path
	 */
	public RemoveCatDirfromString(final AliEnPrincipal user, final String role, final String path) {
		setRequestUser(user);
		setRoleRequest(role);
		this.path = path;
	}

	@Override
	public void run() {
		LFN lfn = LFNUtils.getLFN(path);
		if(lfn!=null)
			wasRemoved = LFNUtils.rmdir(getEffectiveRequester(), lfn);		

	}

	/**
	 * @return the status of the directory's removal
	 */
	public boolean wasRemoved() {
		return this.wasRemoved;
	}


	@Override
	public String toString() {
		return "Asked to remove : " + this.path + ", reply is:\n" + this.wasRemoved;
	}
}
