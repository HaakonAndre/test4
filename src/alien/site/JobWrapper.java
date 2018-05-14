package alien.site;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import alien.api.JBoxServer;
import alien.api.catalogue.CatalogueApiUtils;
import alien.api.taskQueue.TaskQueueApiUtils;
import alien.catalogue.FileSystemUtils;
import alien.catalogue.GUID;
import alien.catalogue.LFN;
import alien.catalogue.PFN;
import alien.catalogue.XmlCollection;
import alien.config.ConfigUtils;
import alien.io.IOUtils;
import alien.monitoring.MonitorFactory;
import alien.monitoring.MonitoringObject;
import alien.shell.commands.JAliEnCOMMander;
import alien.site.packman.PackMan;
import alien.taskQueue.JDL;
import alien.taskQueue.JobStatus;
import alien.user.JAKeyStore;
import alien.user.UserFactory;
import apmon.ApMon;
import apmon.MonitoredJob;

/**
 * Job execution wrapper, running an embedded JBox for in/out-bound communications
 */
public class JobWrapper implements MonitoringObject, Runnable {

	// Folders and files
	private File tempDir = null;
	private static final String defaultOutputDirPrefix = "/jalien-job-";
	private String jobWorkdir = "";

	// Variables passed through VoBox environment
	// private final Map<String, String> env = System.getenv();
	private final String ce;

	// Job variables
	/**
	 * @uml.property  name="jdl"
	 * @uml.associationEnd  
	 */
	private JDL jdl = null;
	private long queueId;
	private String jobToken;
	private String username;
	private String tokenCert;
	private String tokenKey;
	private String workdir = null;
	private HashMap<String, Object> siteMap = new HashMap<>();
	private int payloadPID;
	private MonitoredJob mj;
	/**
	 * @uml.property  name="jobStatus"
	 * @uml.associationEnd  
	 */
	private JobStatus jobStatus;

	// Other
	/**
	 * @uml.property  name="packMan"
	 * @uml.associationEnd  
	 */
	private PackMan packMan = null;
	private String hostName = null;
	private final int pid;
	/**
	 * @uml.property  name="commander"
	 * @uml.associationEnd  
	 */
	//private final JAliEnCOMMander commander = new JAliEnCOMMander();
	private final JAliEnCOMMander commander = JAliEnCOMMander.getInstance();

	/**
	 * @uml.property  name="c_api"
	 * @uml.associationEnd  
	 */
	private final CatalogueApiUtils c_api= new CatalogueApiUtils(commander);
	private static final HashMap<String, Integer> jaStatus = new HashMap<>();

	static {
		jaStatus.put("REQUESTING_JOB", Integer.valueOf(1));
		jaStatus.put("INSTALLING_PKGS", Integer.valueOf(2));
		jaStatus.put("JOB_STARTED", Integer.valueOf(3));
		jaStatus.put("RUNNING_JOB", Integer.valueOf(4));
		jaStatus.put("DONE", Integer.valueOf(5));
		jaStatus.put("ERROR_HC", Integer.valueOf(-1)); // error in getting host
		// classad
		jaStatus.put("ERROR_IP", Integer.valueOf(-2)); // error installing
		// packages
		jaStatus.put("ERROR_GET_JDL", Integer.valueOf(-3)); // error getting jdl
		jaStatus.put("ERROR_JDL", Integer.valueOf(-4)); // incorrect jdl
		jaStatus.put("ERROR_DIRS", Integer.valueOf(-5)); // error creating
		// directories, not
		// enough free space
		// in workdir
		jaStatus.put("ERROR_START", Integer.valueOf(-6)); // error forking to
		// start job
	}

	/**
	 * logger object
	 */
	static transient final Logger logger = ConfigUtils.getLogger(JobWrapper.class.getCanonicalName());

	/**
	 * ML monitor object
	 */
	//	static transient final Monitor monitor = MonitorFactory.getMonitor(JobWrapper.class.getCanonicalName());
	/**
	 * ApMon sender
	 */
	static transient final ApMon apmon = MonitorFactory.getApMonSender();

	/**
	 * Streams for data transfer
	 */
	private ObjectInputStream inputFromJobAgent;
	private ObjectOutputStream outputToJobAgent;

	/**
	 */
	public JobWrapper() {
		
		Map<String, String> env = System.getenv();
		
		for(Map.Entry<String,String> entry : env.entrySet())
			System.err.println(entry.getKey() + " " + entry.getValue());
		

		// TODO: To be put back  
		/* site = env.get("site");
       ConfigUtils.getConfig().gets("alice_close_site").trim();
       ce = env.get("CE"); */
		ce = "ALICE::CERN::Juno"; //TODO: Remove after testing

		//TODO: To be put back
		siteMap = (new SiteMap()).getSiteParameters(env);
		workdir = (String) siteMap.get("workdir");
		hostName = (String) siteMap.get("Host");
		packMan = (PackMan) siteMap.get("PackMan");

		//System.err.println("The JobWrapper user is: " + commander.getUser().getName());
		//System.err.println("With the following certificate: " + commander.getUser().getUserCert()[0]);

		pid = Integer.parseInt(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);

		try {
			inputFromJobAgent = new ObjectInputStream(System.in);
			jdl = (JDL) inputFromJobAgent.readObject();
			username = (String) inputFromJobAgent.readObject();
			queueId = (long) inputFromJobAgent.readObject();
			tokenCert = (String) inputFromJobAgent.readObject();
			tokenKey = (String) inputFromJobAgent.readObject();
			inputFromJobAgent.close();

			System.err.println("We received the following tokenCert: " + tokenCert);
			System.err.println("We received the following tokenKey: " + tokenKey);
			System.err.println("We received the following username: " + username);

			// TODO: Reply to JA?
			/*
			 * String reply = "Wrapper: I just received the following JDL: " + jdl.toString() + "Thanks!";
			 * 
			 * outputToJobAgent = new ObjectOutputStream(System.out);
			 * 
			 * outputToJobAgent.writeObject(reply);
			 * outputToJobAgent.flush();
			 * outputToJobAgent.reset();
			 * outputToJobAgent.close();
			 */
			// TODO: Not needed? We already check if we have a child process from the JobAgent

		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}

		//////////////////////////////////////////////////////
		//////////////////////////////////////////////////////

		if((tokenCert != null) && (tokenKey != null)){
			try {
				JAKeyStore.createTokenFromString(tokenCert, tokenKey);
				System.err.println("Token Created");
				JAKeyStore.loadKeyStore();
			} catch (Exception e) {
				System.err.println("Error. Could not get tokenCert and/or tokenKey");
				e.printStackTrace();
			}
		}

		/*    commander = new JAliEnCOMMander();
    if(commander == null)
      System.err.println("Commander is NULL!");

    c_api = new CatalogueApiUtils(commander);*/   

		//////////////////////////////////////////////////////
		//////////////////////////////////////////////////////

		System.err.println("JobWrapper initialised. Running as the following user: " + commander.getUser().getName());
		
		
		
		
		
		System.err.println(jdl.toHTML().toString());

	}

	@Override
	public void run() {

		logger.log(Level.INFO, "Starting JobWrapper in " + hostName);

		// We start, if needed, the node JBox
		// Does it check a previous one is already running?
		try {
			System.err.println("Trying to start JBox");
			JBoxServer.startJBoxService(0);
		} catch (final Exception e) {
			System.err.println("Unable to start JBox.");
			e.printStackTrace();
		}

		System.err.println("Jbox started");

		// process payload
		runJob();

		cleanup();

		// TODO: Have the JobWrapper report back if something odd happens with the execution(?)
		// TODO: Have the JobWrapper report progress updates(?)

		logger.log(Level.INFO, "JobWrapper has finished execution");
		System.exit(0);
	}

	private void cleanup() {
		System.out.println("Cleaning up after execution...Removing sandbox: " + jobWorkdir);
		// Remove sandbox, TODO: use Java builtin
		//SystemCommand.bash("rm -rf " + jobWorkdir); //TODO: Put back after testing

	}

	private Map<String, String> installPackages(final ArrayList<String> packToInstall) {
		Map<String, String> ok = null;

		for (final String pack : packToInstall) {
			ok = packMan.installPackage(username, pack, null);
			if (ok == null) {
				logger.log(Level.INFO, "Error installing the package " + pack);
				//				monitor.sendParameter("ja_status", "ERROR_IP");
				System.out.println("Error installing " + pack);
				System.exit(1);
			}
		}
		return ok;
	}

	private void runJob() {
		try {
			logger.log(Level.INFO, "Started JobWrapper for: " + jdl);
			
			System.err.println("Attempting to create workdir");

			if (!createWorkDir() || !getInputFiles()) {
				changeStatus(JobStatus.ERROR_IB);
				return;
			}

			System.err.println("Starting execution");

			// run payload
			if (execute() < 0)
				changeStatus(JobStatus.ERROR_E);

			if (!validate())
				changeStatus(JobStatus.ERROR_V);

			if (jobStatus == JobStatus.RUNNING)
				changeStatus(JobStatus.SAVING);

			System.err.println("Finished execution. Preparing to upload files ");

			uploadOutputFiles();

			System.err.println("File upload complete");

		} catch (final Exception e) {
			System.err.println("Unable to handle job");
			e.printStackTrace();
		}
	}

	/**
	 * @param command
	 * @param arguments
	 * @param timeout
	 * @return <code>0</code> if everything went fine, a positive number with the process exit code (which would mean a problem) and a negative error code in case of timeout or other supervised
	 *         execution errors
	 */
	private int executeCommand(final String command, final List<String> arguments) {
		final List<String> cmd = new LinkedList<>();

		final int idx = command.lastIndexOf('/');

		final String cmdStrip = idx < 0 ? command : command.substring(idx + 1);

		final File fExe = new File(tempDir, cmdStrip);

		System.err.println("Checking if file exists");
		
		if (!fExe.exists())
			return -1;

		System.err.println("File exists!");
		
		fExe.setExecutable(true);

		cmd.add(fExe.getAbsolutePath());

		if (arguments != null && arguments.size() > 0)
			for (final String argument : arguments)
				if (argument.trim().length() > 0) {
					final StringTokenizer st = new StringTokenizer(argument);

					while (st.hasMoreTokens())
						cmd.add(st.nextToken());
				}

		System.err.println("Executing: " + cmd + ", arguments is " + arguments + " pid: " + pid);

		final ProcessBuilder pBuilder = new ProcessBuilder(cmd);

		pBuilder.directory(tempDir);

		final HashMap<String, String> environment_packages = getJobPackagesEnvironment();
		final Map<String, String> processEnv = pBuilder.environment();
		processEnv.putAll(environment_packages);
		processEnv.putAll(loadJDLEnvironmentVariables());

		pBuilder.redirectOutput(Redirect.appendTo(new File(tempDir, "stdout")));
		pBuilder.redirectError(Redirect.appendTo(new File(tempDir, "stderr")));
		// pBuilder.redirectErrorStream(true);

		final Process p;

		try {
			changeStatus(JobStatus.RUNNING);
			p = pBuilder.start();

		} catch (final IOException ioe) {
			System.out.println("Exception running " + cmd + " : " + ioe.getMessage());
			return -2;
		}

		mj = new MonitoredJob(pid, jobWorkdir, ce, hostName);
		final Vector<Integer> child = mj.getChildren();
		if (child == null || child.size() <= 1) {
			System.err
			.println("Can't get children. Failed to execute? " + cmd.toString() + " child: " + child);
			return -1;
		}
		System.out.println("Child: " + child.get(1).toString());

		return 0;

	}

	private int execute() {
		commander.q_api.putJobLog(queueId, "trace", "Starting execution");

		final int code = executeCommand(jdl.gets("Executable"), jdl.getArguments());

		System.err.println("Execution code: " + code);

		return code;
	}

	private boolean validate() {
		int code = 0;

		final String validation = jdl.gets("ValidationCommand");

		if (validation != null) {
			commander.q_api.putJobLog(queueId, "trace", "Starting validation");
			code = executeCommand(validation, null);
		}
		System.err.println("Validation code: " + code);

		return code == 0;
	}

	private boolean getInputFiles() {
		final Set<String> filesToDownload = new HashSet<>();

		List<String> list = jdl.getInputFiles(false);

		if (list != null)
			filesToDownload.addAll(list);

		list = jdl.getInputData(false);

		if (list != null)
			filesToDownload.addAll(list);

		String s = jdl.getExecutable();

		if (s != null)
			filesToDownload.add(s);

		s = jdl.gets("ValidationCommand");

		if (s != null)
			filesToDownload.add(s);

		final List<LFN> iFiles = c_api.getLFNs(filesToDownload, true, false);

		if (iFiles == null || iFiles.size() != filesToDownload.size()) {
			System.out.println("Not all requested files could be located");
			return false;
		}

		final Map<LFN, File> localFiles = new HashMap<>();

		for (final LFN l : iFiles) {
			File localFile = new File(tempDir, l.getFileName());

			final int i = 0;

			while (localFile.exists() && i < 100000)
				localFile = new File(tempDir, l.getFileName() + "." + i);

			if (localFile.exists()) {
				System.out.println("Too many occurences of " + l.getFileName() + " in " + tempDir.getAbsolutePath());
				return false;
			}

			localFiles.put(l, localFile);
		}

		for (final Map.Entry<LFN, File> entry : localFiles.entrySet()) {
			final List<PFN> pfns = c_api.getPFNsToRead(entry.getKey(), null, null);

			if (pfns == null || pfns.size() == 0) {
				System.out.println("No replicas of " + entry.getKey().getCanonicalName() + " to read from");
				return false;
			}

			final GUID g = pfns.iterator().next().getGuid();

			commander.q_api.putJobLog(queueId, "trace", "Getting InputFile: " + entry.getKey().getCanonicalName());

			System.out.println("GUID g: " + g + " entry.getvalue(): " + entry.getValue());

			final File f = IOUtils.get(g, entry.getValue());

			if (f == null) {
				System.out.println("Could not download " + entry.getKey().getCanonicalName() + " to " + entry.getValue().getAbsolutePath());
				return false;
			}
		}

		dumpInputDataList();

		System.out.println("Sandbox prepared : " + tempDir.getAbsolutePath());

		return true;
	}

	private void dumpInputDataList() {
		// creates xml file with the InputData
		try {
			final String list = jdl.gets("InputDataList");

			if (list == null)
				return;

			System.out.println("Going to create XML: " + list);

			final String format = jdl.gets("InputDataListFormat");
			if (format == null || !format.equals("xml-single")) {
				System.out.println("XML format not understood");
				return;
			}

			final XmlCollection c = new XmlCollection();
			c.setName("jobinputdata");
			final List<String> datalist = jdl.getInputData(true);

			for (final String s : datalist) {
				final LFN l = c_api.getLFN(s);
				if (l == null)
					continue;
				c.add(l);
			}

			final String content = c.toString();

			Files.write(Paths.get(jobWorkdir + "/" + list), content.getBytes());

		} catch (final Exception e) {
			System.out.println("Problem dumping XML: " + e.toString());
		}

	}

	private HashMap<String, String> getJobPackagesEnvironment() {
		final String voalice = "VO_ALICE@";
		String packagestring = "";
		final HashMap<String, String> packs = (HashMap<String, String>) jdl.getPackages();
		HashMap<String, String> envmap = new HashMap<>();

		if (packs != null) {
			for (final String pack : packs.keySet())
				packagestring += voalice + pack + "::" + packs.get(pack) + ",";

			if (!packs.containsKey("APISCONFIG"))
				packagestring += voalice + "APISCONFIG,";

			packagestring = packagestring.substring(0, packagestring.length() - 1);

			final ArrayList<String> packagesList = new ArrayList<>();
			packagesList.add(packagestring);

			logger.log(Level.INFO, packagestring);

			envmap = (HashMap<String, String>) installPackages(packagesList);
		}

		logger.log(Level.INFO, envmap.toString());
		return envmap;
	}

	private boolean uploadOutputFiles() {
		boolean uploadedAllOutFiles = true;
		boolean uploadedNotAllCopies = false;

		commander.q_api.putJobLog(queueId, "trace", "Going to uploadOutputFiles");

		final String outputDir = getJobOutputDir();

		System.out.println("queueId: " + queueId);
		System.out.println("outputDir: " + outputDir);
		System.out.println("We are the current user: "  + commander.getUser().getName());

		if (c_api.getLFN(outputDir) == null) {
			final LFN outDir = c_api.createCatalogueDirectory(outputDir);
			if (outDir == null) {
				System.err.println("Error creating the OutputDir [" + outputDir + "].");
				changeStatus(JobStatus.ERROR_SV);
				return false;
			}
		}

		String tag = "Output";
		if (jobStatus == JobStatus.ERROR_E)
			tag = "OutputErrorE";

		final ParsedOutput filesTable = new ParsedOutput(queueId, jdl, jobWorkdir, tag);

		for (final OutputEntry entry : filesTable.getEntries()) {
			File localFile;
			ArrayList<String> filesIncluded = null;
			try {
				if (entry.isArchive())
					filesIncluded = entry.createZip(jobWorkdir);

				localFile = new File(jobWorkdir + "/" + entry.getName());
				System.out.println("Processing output file: " + localFile);

				if (localFile.exists() && localFile.isFile() && localFile.canRead() && localFile.length() > 0) {
					// Use upload instead
					commander.q_api.putJobLog(queueId, "trace", "Uploading: " + entry.getName());

					final ByteArrayOutputStream out = new ByteArrayOutputStream();
					IOUtils.upload(localFile, outputDir + "/" + entry.getName(), UserFactory.getByUsername(username), out, "-w", "-S",
							(entry.getOptions() != null && entry.getOptions().length() > 0 ? entry.getOptions().replace('=', ':') : "disk:2"), "-j", String.valueOf(queueId));
					final String output_upload = out.toString("UTF-8");
					final String lower_output = output_upload.toLowerCase();

					System.out.println("Output upload: " + output_upload);

					if (lower_output.contains("only")) {
						uploadedNotAllCopies = true;
						commander.q_api.putJobLog(queueId, "trace", output_upload);
						break;
					}
					else
						if (lower_output.contains("failed")) {
							uploadedAllOutFiles = false;
							commander.q_api.putJobLog(queueId, "trace", output_upload);
							break;
						}

					if (filesIncluded != null) {
						// Register lfn links to archive
						CatalogueApiUtils.registerEntry(entry, outputDir + "/", UserFactory.getByUsername(username));
					}

				}
				else {
					System.out.println("Can't upload output file " + localFile.getName() + ", does not exist or has zero size.");
					commander.q_api.putJobLog(queueId, "trace", "Can't upload output file " + localFile.getName() + ", does not exist or has zero size.");
				}

			} catch (final IOException e) {
				e.printStackTrace();
				uploadedAllOutFiles = false;
			}
		}
		// }

		if (jobStatus != JobStatus.ERROR_E && jobStatus != JobStatus.ERROR_V) {
			if (!uploadedAllOutFiles)
				changeStatus(JobStatus.ERROR_SV);
			else
				if (uploadedNotAllCopies)
					changeStatus(JobStatus.DONE_WARN);
				else
					changeStatus(JobStatus.DONE);
		}

		return uploadedAllOutFiles;
	}

	private boolean createWorkDir() {
		
		System.err.println("Creating workdir");
		
		logger.log(Level.INFO, "Creating sandbox and chdir");

		jobWorkdir = String.format("%s%s%d", workdir, defaultOutputDirPrefix, Long.valueOf(queueId));

		tempDir = new File(jobWorkdir);
		if (!tempDir.exists()) {
			final boolean created = tempDir.mkdirs();
			if (!created) {
				logger.log(Level.INFO, "Workdir does not exist and can't be created: " + jobWorkdir);
				System.err.println("Creating workdir failed");
				return false;
			}
		}

		// chdir
		System.setProperty("user.dir", jobWorkdir);

		commander.q_api.putJobLog(queueId, "trace", "Created workdir: " + jobWorkdir);
		// TODO: create the extra directories

		System.err.println("Creating workdir succeeded");
		return true;
	}

	private HashMap<String, String> loadJDLEnvironmentVariables() {
		final HashMap<String, String> hashret = new HashMap<>();

		try {
			final HashMap<String, Object> vars = (HashMap<String, Object>) jdl.getJDLVariables();

			if (vars != null)
				for (final String s : vars.keySet()) {
					String value = "";
					final Object val = jdl.get(s);

					if (val instanceof Collection<?>) {
						@SuppressWarnings("unchecked")
						final Iterator<String> it = ((Collection<String>) val).iterator();
						String sbuff = "";
						boolean isFirst = true;

						while (it.hasNext()) {
							if (!isFirst)
								sbuff += "##";
							final String v = it.next().toString();
							sbuff += v;
							isFirst = false;
						}
						value = sbuff;
					}
					else
						value = val.toString();

					hashret.put("ALIEN_JDL_" + s.toUpperCase(), value);
				}
		} catch (final Exception e) {
			System.out.println("There was a problem getting JDLVariables: " + e);
		}

		return hashret;
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(final String[] args) throws IOException {
		final JobWrapper jw = new JobWrapper();
		jw.run();
	}

	/**
	 * @param newStatus
	 */
	public void changeStatus(final JobStatus newStatus) {
		final HashMap<String, Object> extrafields = new HashMap<>();
		extrafields.put("exechost", this.ce);
		// if final status with saved files, we set the path
		if (newStatus == JobStatus.DONE || newStatus == JobStatus.DONE_WARN || newStatus == JobStatus.ERROR_E || newStatus == JobStatus.ERROR_V) {
			extrafields.put("path", getJobOutputDir());

			TaskQueueApiUtils.setJobStatus(queueId, newStatus, extrafields);
		}
		else
			if (newStatus == JobStatus.RUNNING) {
				extrafields.put("spyurl", hostName + ":" + JBoxServer.getPort());
				extrafields.put("node", hostName);

				TaskQueueApiUtils.setJobStatus(queueId, newStatus, extrafields);
			}
			else
				TaskQueueApiUtils.setJobStatus(queueId, newStatus);

		jobStatus = newStatus;

		return;
	}

	/**
	 * @return job output dir (as indicated in the JDL if OK, or the recycle path if not)
	 */
	public String getJobOutputDir() {
		String outputDir = jdl.getOutputDir();

		if (jobStatus == JobStatus.ERROR_V || jobStatus == JobStatus.ERROR_E)
			outputDir = FileSystemUtils.getAbsolutePath(username, null, "~" + "recycle/" + defaultOutputDirPrefix + queueId);
		else
			if (outputDir == null)
				outputDir = FileSystemUtils.getAbsolutePath(username, null, "~" + defaultOutputDirPrefix + queueId);

		return outputDir;
	}

	@Override
	public void fillValues(final Vector<String> paramNames, final Vector<Object> paramValues) {
		if (queueId > 0) {
			paramNames.add("jobID");
			paramValues.add(Double.valueOf(queueId));

			paramNames.add("statusID");
			paramValues.add(Double.valueOf(jobStatus.getAliEnLevel()));
		}
	}

}
