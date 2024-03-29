package alien.site.supercomputing.titan;

/**
 * @author psvirin
 *
 */
public class TitanJobStatus {
	final int rank;
	/**
	 * Job that is executed
	 */
	public Long queueId;
	String jobFolder;
	String status;
	int executionCode;
	int validationCode;
	final TitanBatchInfo batch;

	/**
	 * Initialize all fields
	 *
	 * @param r
	 * @param qid
	 * @param job_folder
	 * @param st
	 * @param exec_code
	 * @param val_code
	 * @param bi
	 */
	public TitanJobStatus(final int r, final Long qid, final String job_folder, final String st, final int exec_code, final int val_code, final TitanBatchInfo bi) {
		rank = r;
		queueId = qid;
		jobFolder = job_folder;
		status = st;
		executionCode = exec_code;
		validationCode = val_code;
		batch = bi;
	}
}
