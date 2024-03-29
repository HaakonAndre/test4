package alien.api;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import alien.config.ConfigUtils;
import alien.monitoring.Monitor;
import alien.monitoring.MonitorFactory;
import alien.shell.commands.JAliEnCOMMander;
import alien.shell.commands.JSONPrintWriter;
import alien.shell.commands.UIPrintWriter;
import alien.user.AliEnPrincipal;
import alien.user.JAKeyStore;
import alien.user.LdapCertificateRealm;
import alien.user.UserFactory;
import lazyj.commands.CommandOutput;
import lazyj.commands.SystemCommand;

/**
 * @author yuw
 *
 */
public class TomcatServer {

	/**
	 * Logger
	 */
	static transient final Logger logger = ConfigUtils.getLogger(JBoxServer.class.getCanonicalName());

	static transient final Monitor monitor = MonitorFactory.getMonitor(TomcatServer.class.getCanonicalName());

	/**
	 * Web server instance
	 */
	final Tomcat tomcat;

	private final int websocketPort;

	/**
	 * Start the Tomcat server on a given port
	 *
	 * @param tomcatPort
	 */
	private TomcatServer(final int tomcatPort) throws Exception {
		this.websocketPort = tomcatPort;

		tomcat = new Tomcat();
		tomcat.setBaseDir(System.getProperty("java.io.tmpdir"));
		tomcat.setPort(tomcatPort);
		final Service service = tomcat.getService();
		tomcat.getService().removeConnector(tomcat.getConnector()); // remove default connector
		service.addConnector(createSslConnector(tomcatPort));
		final LdapCertificateRealm ldapRealm = new LdapCertificateRealm();
		tomcat.getEngine().setRealm(ldapRealm);

		// Configure websocket webapplication

		// Add a dummy ROOT context
		final StandardContext ctx = (StandardContext) tomcat.addWebapp("", new File(System.getProperty("java.io.tmpdir")).getAbsolutePath());

		// disable per context work directories too
		ctx.setWorkDir(System.getProperty("java.io.tmpdir"));

		// Set security constraints in order to use AlienUserPrincipal later
		final SecurityCollection securityCollection = new SecurityCollection();
		securityCollection.addPattern("/*");
		final SecurityConstraint securityConstraint = new SecurityConstraint();
		securityConstraint.addCollection(securityCollection);
		securityConstraint.setAuthConstraint(true);
		securityConstraint.setUserConstraint("CONFIDENTIAL");
		securityConstraint.addAuthRole("users");
		// ctx.addSecurityRole("users123");
		ctx.addConstraint(securityConstraint);

		final LoginConfig loginConfig = new LoginConfig();
		loginConfig.setAuthMethod("CLIENT-CERT");
		loginConfig.setRealmName(LdapCertificateRealm.class.getCanonicalName());
		ctx.setLoginConfig(loginConfig);
		ctx.setRealm(ldapRealm);

		// ctx.getPipeline().addValve(new SSLAuthenticator());

		// Tell Jar Scanner not to look inside jar manifests
		// otherwise it will produce useless warnings
		final StandardJarScanner jarScanner = (StandardJarScanner) ctx.getJarScanner();
		jarScanner.setScanManifest(false);

		// // Add a dummy ROOT context
		// final StandardContext ctx = (StandardContext) tomcat.addContext("", null);
		//
		// // Configure websocket application
		// ctx.addApplicationListener(TesterEndpointConfig.class.getName());
		//
		//
		// // Set security constraints in order to use AlienUserPrincipal later
		// final SecurityCollection securityCollection = new SecurityCollection();
		// securityCollection.addPattern("/*");
		// final SecurityConstraint securityConstraint = new SecurityConstraint();
		// securityConstraint.addCollection(securityCollection);
		// securityConstraint.setUserConstraint("CONFIDENTIAL");
		//
		// final LoginConfig loginConfig = new LoginConfig();
		// loginConfig.setAuthMethod("CLIENT-CERT");
		// loginConfig.setRealmName(LdapCertificateRealm.class.getCanonicalName());
		// ctx.setLoginConfig(loginConfig);
		//
		// securityConstraint.addAuthRole("users");
		// ctx.addSecurityRole("users");
		// ctx.addConstraint(securityConstraint);

		tomcat.start();
		if (tomcat.getService().findConnectors()[0].getState() == LifecycleState.FAILED)
			throw new BindException();

		final Executor executor = tomcat.getConnector().getProtocolHandler().getExecutor();

		if (executor instanceof ThreadPoolExecutor) {
			final ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;

			monitor.addMonitoring("server_status", (names, values) -> {
				names.add("active_threads");
				values.add(Double.valueOf(tpe.getActiveCount()));

				names.add("max_threads");
				values.add(Double.valueOf(tpe.getMaximumPoolSize()));
			});
		}
		else
			logger.log(Level.SEVERE, "Cannot monitor Tomcat executor of type " + executor.getClass().getCanonicalName());

		// Let Tomcat run in another thread so it will keep on waiting forever
		new Thread() {
			@Override
			public void run() {
				tomcat.getServer().await();
			}
		}.start();

		if (!ConfigUtils.isCentralService())
			// Refresh token cert every two hours
			new Thread() {
				@Override
				public void run() {
					try {
						while (true) {
							sleep(2 * 60 * 60 * 1000);
							requestTokenCert();
						}
					}
					catch (final InterruptedException e) {
						e.printStackTrace();
					}
				}
			}.start();
	}

	/**
	 * Create SSL connector for the Tomcat server
	 *
	 * @param tomcatPort
	 * @throws Exception
	 */
	private static Connector createSslConnector(final int tomcatPort) throws Exception {
		final String keystorePass = new String(JAKeyStore.pass);

		final String dirName = System.getProperty("java.io.tmpdir") + File.separator;
		final String keystoreName = dirName + "keystore.jks_" + UserFactory.getUserID();
		final String truststoreName = dirName + "truststore.jks_" + UserFactory.getUserID();

		if (ConfigUtils.isCentralService())
			JAKeyStore.saveKeyStore(JAKeyStore.getKeyStore(), keystoreName, JAKeyStore.pass);
		else
			JAKeyStore.saveKeyStore(JAKeyStore.tokenCert, keystoreName, JAKeyStore.pass);

		JAKeyStore.saveKeyStore(JAKeyStore.trustStore, truststoreName, JAKeyStore.pass);

		final Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");

		connector.setPort(tomcatPort);
		connector.setSecure(true);
		connector.setScheme("https");
		connector.setAttribute("keyAlias", "User.cert");
		connector.setAttribute("keystorePass", keystorePass);
		connector.setAttribute("keystoreType", "JKS");
		connector.setAttribute("keystoreFile", keystoreName);
		connector.setAttribute("truststorePass", keystorePass);
		connector.setAttribute("truststoreType", "JKS");
		connector.setAttribute("truststoreFile", truststoreName);
		connector.setAttribute("clientAuth", "true");
		connector.setAttribute("sslProtocol", "TLS");
		connector.setAttribute("SSLEnabled", "true");
		connector.setAttribute("maxThreads", "200");
		connector.setAttribute("connectionTimeout", "20000");
		return connector;
	}

	/**
	 * Change permissions of the file
	 */
	private static boolean changeMod(final File file, final int chmod) {
		if (file.exists())
			try {
				final CommandOutput co = SystemCommand.bash("chmod " + chmod + " " + file.getCanonicalPath(), false);

				if (co.exitCode != 0)
					System.err.println("Could not change permissions: " + co.stderr);

				return co.exitCode == 0;

			}
			catch (@SuppressWarnings("unused") final IOException e) {
				// ignore
			}
		return false;
	}

	/**
	 * Request token certificate from JCentral
	 *
	 * @return true if tokencert was successfully received
	 */
	static boolean requestTokenCert() {
		// Get user certificate to connect to JCentral
		Certificate[] cert = null;
		AliEnPrincipal userIdentity = null;
		try {
			cert = JAKeyStore.getKeyStore().getCertificateChain("User.cert");
			if (cert == null) {
				logger.log(Level.SEVERE, "Failed to load certificate");
				return false;
			}
		}
		catch (final KeyStoreException e) {
			e.printStackTrace();
		}

		if (cert instanceof X509Certificate[]) {
			final X509Certificate[] x509cert = (X509Certificate[]) cert;
			userIdentity = UserFactory.getByCertificate(x509cert);
		}
		if (userIdentity == null) {
			logger.log(Level.SEVERE, "Failed to get user identity");
			return false;
		}

		final String sUserId = UserFactory.getUserID();

		if (sUserId == null) {
			logger.log(Level.SEVERE, "Cannot get the current user's ID");
			return false;
		}

		// Two files will be the result of this command
		// Check if their location is set by env variables or in config, otherwise put default location in $TMPDIR/
		final String tokencertpath = ConfigUtils.getConfig().gets("tokencert.path", System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + "tokencert_" + sUserId + ".pem");
		final String tokenkeypath = ConfigUtils.getConfig().gets("tokenkey.path", System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + "tokenkey_" + sUserId + ".pem");

		final File tokencertfile = new File(tokencertpath);
		final File tokenkeyfile = new File(tokenkeypath);

		// Allow to modify those files if they already exist
		changeMod(tokencertfile, 777);
		changeMod(tokenkeyfile, 777);

		try ( // Open files for writing
				PrintWriter pwritercert = new PrintWriter(tokencertfile);
				PrintWriter pwriterkey = new PrintWriter(tokenkeyfile);

				// We will read all data into temp output stream and then parse it and split into 2 files
				ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			final UIPrintWriter out = new JSONPrintWriter(baos);

			// Create Commander instance just to execute one command
			final JAliEnCOMMander commander = new JAliEnCOMMander(userIdentity, null, null, out);
			commander.start();

			// Command to be sent (yes, we need it to be an array, even if it is one word)
			final ArrayList<String> fullCmd = new ArrayList<>();
			fullCmd.add("token");

			synchronized (commander) {
				commander.status.set(1);
				commander.setLine(out, fullCmd.toArray(new String[0]));
				commander.notifyAll();
			}

			while (commander.status.get() == 1)
				try {
					synchronized (commander.status) {
						commander.status.wait(1000);
					}
				}
				catch (@SuppressWarnings("unused") final InterruptedException ie) {
					// ignore
				}

			// Now parse the reply from JCentral
			final JSONParser jsonParser = new JSONParser();
			final JSONObject readf = (JSONObject) jsonParser.parse(baos.toString());
			final JSONArray jsonArray = (JSONArray) readf.get("results");
			for (final Object object : jsonArray) {
				final JSONObject aJson = (JSONObject) object;
				pwritercert.print(aJson.get("tokencert"));
				pwriterkey.print(aJson.get("tokenkey"));
				pwritercert.flush();
				pwriterkey.flush();
			}

			// Set correct permissions
			changeMod(tokencertfile, 440);
			changeMod(tokenkeyfile, 400);

			// Execution finished - kill commander
			commander.kill = true;
			return true;

		}
		catch (final Exception e) {
			logger.log(Level.SEVERE, "Token request failed", e);
			return false;
		}
	}

	/**
	 * Singleton
	 */
	static TomcatServer tomcatServer = null;

	/**
	 * Start Tomcat Server
	 */
	public static synchronized void startTomcatServer() {

		if (tomcatServer != null)
			return;

		logger.log(Level.INFO, "Tomcat starting ...");

		// Request token certificate from JCentral
		if (!ConfigUtils.isCentralService()) {
			if (!requestTokenCert())
				return;
			// Create keystore for token certificate
			try {
				if (!JAKeyStore.loadTokenKeyStorage()) {
					System.err.println("Token Certificate could not be loaded.");
					System.err.println("Exiting...");
					return;
				}
			}
			catch (final Exception e) {
				logger.log(Level.SEVERE, "Error loading token", e);
				System.err.println("Error loading token");
				return;
			}
		}
		// Set dynamic port range for Tomcat server
		final int portMin = Integer.parseInt(ConfigUtils.getConfig().gets("port.range.start", "10100"));
		final int portMax = Integer.parseInt(ConfigUtils.getConfig().gets("port.range.end", "10700"));
		int port = 8097;

		// Try to launch Tomcat on default port
		try (ServerSocket ssocket = new ServerSocket(port, 10, InetAddress.getByName("127.0.0.1"))) // Fast check if port is available
		{
			ssocket.close();

			// Actually start Tomcat
			tomcatServer = new TomcatServer(port);

			logger.log(Level.INFO, "Tomcat listening on port " + port);
			System.out.println("Tomcat is listening on port " + port);
			return; // Everything's ok, exit

		}
		catch (final Exception ioe) {
			// Port is already in use, maybe there's another user on the machine...
			logger.log(Level.FINE, "Tomcat: Could not listen on port " + port, ioe);
		}

		// Try another ports in range
		for (port = portMin; port < portMax; port++)
			try (ServerSocket ssocket = new ServerSocket(port, 10, InetAddress.getByName("127.0.0.1"))) // Fast check if port is available
			{
				ssocket.close();
				// Actually start Tomcat
				tomcatServer = new TomcatServer(port);

				logger.log(Level.INFO, "Tomcat listening on port " + port);
				System.out.println("Tomcat is listening on port " + port);
				break;
			}
			catch (final Exception ioe) {
				// Try next one
				logger.log(Level.FINE, "Tomcat: Could not listen on port " + port, ioe);
			}
	}

	/**
	 *
	 * Get the port used by tomcatServer
	 *
	 * @return the TCP port this server is listening on. Can be negative to signal that the server is actually not listening on any port (yet?)
	 *
	 */
	public static int getPort() {
		return tomcatServer != null ? tomcatServer.websocketPort : -1;
	}

}
