package alien.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import alien.config.ConfigUtils;
import alien.monitoring.MonitorFactory;
import alien.shell.commands.JAliEnCOMMander;
import alien.user.JAKeyStore;

/**
 * Simple UI server to be used by ROOT and command line
 * 
 * @author costing
 */
public class APIServer extends Thread {

	/**
	 * Logger
	 */
	static transient final Logger logger = ConfigUtils
			.getLogger(APIServer.class.getCanonicalName());

	private final int port;

	private ServerSocket ssocket;

	/**
	 * The password
	 */
	final String password;

	/**
	 * Start the server on a given port
	 * 
	 * @param listeningPort
	 * @throws IOException
	 */
	private APIServer(final int listeningPort) throws IOException {
		this.port = listeningPort;

		InetAddress localhost = InetAddress.getByName("127.0.0.1");

		System.err.println("Trying to bind to " + localhost + " : " + port);

		ssocket = new ServerSocket(port, 10, localhost);

		password = UUID.randomUUID().toString();

		final File fHome = new File(System.getProperty("user.home"));

		final File f = new File(fHome, ".alien");

		f.mkdirs();

		FileWriter fw;
		try {
			fw = new FileWriter(new File(f, ".uisession"));

			fw.write("127.0.0.1:" + port + "\n" + password + "\n"
					+ MonitorFactory.getSelfProcessID() + "\n");
			fw.flush();
			fw.close();
		} catch (IOException e) {
			ssocket.close();

			throw e;
		}
	}

	/**
	 * One UI connection
	 * 
	 * @author costing
	 */
	private class UIConnection extends Thread {
		private final Socket s;

		private final InputStream is;

		private final OutputStream os;

		/**
		 * One UI connection identified by the socket
		 * 
		 * @param s
		 * @throws IOException
		 */
		public UIConnection(final Socket s) throws IOException {
			this.s = s;
			is = s.getInputStream();
			os = s.getOutputStream();

			setName("UIConnection: " + s.getInetAddress());
		}

		@Override
		public void run() {
			try {
				BufferedReader br = new BufferedReader(
						new InputStreamReader(is));

				String sLine = br.readLine();

				if (sLine.equals(password))
					System.out.println("password accepted");

				if (sLine == null || !sLine.equals(password))
					return;
				JAliEnCOMMander jcomm = new JAliEnCOMMander();
				while ((sLine = br.readLine()) != null) {
					System.out.println("we received call: " + sLine);
					jcomm.execute(os, sLine.trim().split(" "));
					os.flush();
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					s.shutdownOutput();
				} catch (IOException e) {
					// nothing particular
				}
				try {
					s.shutdownInput();
				} catch (IOException e) {
					// ignore
				}
				try {
					s.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				final Socket s = ssocket.accept();

				final UIConnection conn = new UIConnection(s);

				conn.start();
			} catch (IOException e) {
				continue;
			}
		}
	}

	private static APIServer server = null;

	/**
	 * Start once the UIServer
	 */
	private static synchronized void startAPIServer() {
		if (server != null)
			return;

		for (int port = 10100; port < 10200; port++) {
			try {
				server = new APIServer(port);
				server.start();

				logger.log(Level.INFO, "UIServer listening on port " + port);
				System.err.println("Listening on " + port);

				break;
			} catch (IOException ioe) {
				System.err.println(ioe);
				ioe.printStackTrace();
				logger.log(Level.FINE, "Could not listen on port " + port, ioe);
			}
		}
	}

	
	/**
	 * 
	 * Load necessary keys and start APIServer
	 */
	public static void startAPIService() {
		try {
			JAKeyStore.loadClientKeyStorage();
			APIServer.startAPIServer();
		} catch (org.bouncycastle.openssl.EncryptionException e) {
			System.err.println("Wrong password!");
		} catch (javax.crypto.BadPaddingException e) {
			System.err.println("Wrong password!");
		}

		catch (Exception e) {
			System.err.println("Error loading the key");
			e.printStackTrace();
		}

	}

}