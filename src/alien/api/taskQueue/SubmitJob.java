package alien.api.taskQueue;

import alien.api.Request;
import alien.taskQueue.JobSubmissionException;
import alien.taskQueue.TaskQueueFakeUtils;

/**
 * Get a JDL object
 * 
 * @author ron
 * @since Jun 05, 2011
 */
public class SubmitJob extends Request {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7349968366381661013L;
	
	
	private final String jdl;
	private int jobID = 0;
	private String reason = null;

	/**
	 * @param jdl
	 */
	public SubmitJob(String jdl) {
		this.jdl = jdl;
	}

	@Override
	public void run() {
		System.out.println("received jdl submit...");
		try {
			this.jobID = TaskQueueFakeUtils.submitJob(this.jdl,
					this.getPartnerIdentity(), this.getPartnerCertificate());
			System.out.println("submitted job with ID:" + this.jobID);
		} catch (JobSubmissionException e) {
			this.reason = e.getMessage();
			System.out.println("caught JobSubmissionException: "
					+ e.getMessage());
		}

	}

	/**
	 * @return jobID
	 * @throws JobSubmissionException
	 */
	public int getJobID() throws JobSubmissionException {
		System.out.println("job received ID:" + this.jobID);

		if (this.reason != null)
			throw new JobSubmissionException(reason);

		if (this.jobID == 0)
			throw new JobSubmissionException(
					"There was a problem during the submission transaction.");

		return this.jobID;
	}

	@Override
	public String toString() {
		return "Asked to submit JDL: " + this.jdl;
	}
}