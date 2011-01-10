package alien.services;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.ExtProperties;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;

import alien.catalogue.access.XrootDEnvelope;
import alien.config.ConfigUtils;
import alien.tsealedEnvelope.EncryptedAuthzToken;

/**
 * @author ron
 * @since Nov 14, 2010
 */
public class XrootDEnvelopeSigner {
	
	/**
	 * logger
	 */
	static transient final Logger logger = ConfigUtils.getLogger(XrootDEnvelopeSigner.class.getCanonicalName());

	private static final String AuthenPrivLocation;
	private static final String AuthenPubLocation;
	private static final String SEPrivLocation;
	private static final String SEPubLocation;

	private static final RSAPrivateKey AuthenPrivKey;
	private static final RSAPublicKey AuthenPubKey;
	private static final RSAPrivateKey SEPrivKey;
	private static final RSAPublicKey SEPubKey;
	
	/**
	 * load the RSA keys for envelope signature, keys are supposed to be in pem,
	 * and can be created with: openssl req -x509 -nodes -days 365 -newkey
	 * rsa:4096 -keyout lpriv.pem -out lpub.pem
	 */
	static{
		Security.addProvider(new BouncyCastleProvider());

		ExtProperties config = ConfigUtils.getConfig();
		
		final String authenKeysLocation = config.gets("Authen.keys.location", System.getProperty("user.home")+System.getProperty("file.separator")+".alien"+System.getProperty("file.separator")+"authen"+System.getProperty("file.separator"));
		
		final String seKeysLocation = config.gets("SE.keys.location", authenKeysLocation);
		
		AuthenPrivLocation = authenKeysLocation+"lpriv.pem";
		AuthenPubLocation = authenKeysLocation+"lpub.pem";
		SEPrivLocation = seKeysLocation+"rpriv.pem";
		SEPubLocation = seKeysLocation+"rpub.pem";

		RSAPrivateKey authenPrivKey = null;
		RSAPublicKey  authenPubKey = null;
		RSAPrivateKey sePrivKey = null;
		RSAPublicKey  sePubKey = null;
		
		try{
			authenPrivKey = (RSAPrivateKey) ((KeyPair) new PEMReader(
					new BufferedReader(new FileReader(AuthenPrivLocation)))
					.readObject()).getPrivate();
			authenPubKey = (RSAPublicKey) ((X509Certificate) new PEMReader(
					new BufferedReader(new FileReader(AuthenPubLocation)))
					.readObject()).getPublicKey();
		}
		catch (IOException ioe){
			logger.log(Level.WARNING, "Authen keys could not be loaded from "+authenKeysLocation);
		}
		
		try{
			sePrivKey = (RSAPrivateKey) ((KeyPair) new PEMReader(
					new BufferedReader(new FileReader(SEPrivLocation)))
					.readObject()).getPrivate();
			sePubKey = (RSAPublicKey) ((X509Certificate) new PEMReader(
					new BufferedReader(new FileReader(SEPubLocation))).readObject())
					.getPublicKey();
		}
		catch (IOException ioe){
			logger.log(Level.WARNING, "SE keys could not be loaded from "+seKeysLocation);
		}
		
		AuthenPrivKey = authenPrivKey;
		AuthenPubKey = authenPubKey;
		SEPrivKey = sePrivKey;
		SEPubKey = sePubKey;
	}
	
	private static EncryptedAuthzToken authz = null;
	
	private static String localHostName = null;
	
	private static final String getLocalHostName(){
		if (localHostName==null){
			try{
				localHostName = java.net.InetAddress.getLocalHost().getHostName();
			}
			catch (Exception e){
				// ignore
			}
		}
		
		return localHostName;
	}

	/**
	 * @param envelope
	 * @param selfSigned
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 * @throws SignatureException
	 */
	public static void signEnvelope(final XrootDEnvelope envelope, final boolean selfSigned)
			throws NoSuchAlgorithmException, InvalidKeyException,
			SignatureException {

		// System.out.println("About to be signed: " +
		// envelope.getUnsignedEnvelope());

		final long issued = System.currentTimeMillis() / 1000L;
		final long expires = issued + 86400;

		final String toBeSigned = envelope.getUnsignedEnvelope()
				+ "&issuer=JAuthenX@"+getLocalHostName()+"&issued=" + issued + "&expires=" + expires
				+ "&hashord=" + XrootDEnvelope.hashord
				+ "-issuer-issued-expires-hashord";

		final Signature signer = Signature.getInstance("SHA384withRSA");
		
		if (selfSigned){
			signer.initSign(SEPrivKey);
		}
		else {
			signer.initSign(AuthenPrivKey);
		}
		
		signer.update(toBeSigned.getBytes());

		final byte[] rawsignature = signer.sign();

		envelope.setSignedEnvelope(toBeSigned + "&signature="
				+ String.valueOf(Base64.encode(rawsignature)));

		// System.out.println("We signed: " + envelope.getSignedEnvelope());

	}

	/**
	 * @param envelope
	 * @throws GeneralSecurityException
	 */
	public static void encryptEnvelope(final XrootDEnvelope envelope) throws GeneralSecurityException {

		// System.out.println("About to be encrypted: " +
		// envelope.getUnEncryptedEnvelope());

		if (authz == null)
			authz = new EncryptedAuthzToken(AuthenPrivKey, SEPubKey);

		envelope.setEncryptedEnvelope(authz.encrypt(envelope
				.getUnEncryptedEnvelope()));
		// System.out.println("We encrypted: " +
		// envelope.getEncryptedEnvelope());
	}

	//
	//
	//
	//
	//
	//
	// /**
	// * @param ca
	// * @param sitename
	// * @param qos
	// * @param qosCount
	// * @param staticSEs
	// * @param exxSes
	// */
	// public static void loadXrootDEnvelopesForCatalogueAccess(
	// CatalogueAccess ca, String sitename, String qos, int qosCount,
	// Set<SE> staticSEs, Set<SE> exxSes) {
	//
	// if (ca.getAccess() == CatalogueAccess.READ
	// || ca.getAccess() == CatalogueAccess.DELETE) {
	//
	// ca.loadPFNS();
	//
	// final Set<PFN> whereis = ca.getPFNS();
	//
	// // keep all replicas, even if the SE is reported as broken (though
	// // it will go to the end of list)
	// final List<PFN> sorted = SEUtils.sortBySite(whereis, sitename,
	// false);
	//
	// // remove the replicas from these SEs, if specified
	// if (exxSes != null && exxSes.size() > 0) {
	// final Iterator<PFN> it = sorted.iterator();
	//
	// while (it.hasNext()) {
	// final PFN pfn = it.next();
	//
	// final SE se = SEUtils.getSE(pfn.seNumber);
	//
	// if (exxSes.contains(se))
	// it.remove();
	// }
	// }
	//
	// // getEnvelopesforPFNList(ca, sorted);
	// if (sorted.size() > 0)
	// ca.addEnvelope(new XrootDEnvelope(ca, sorted.get(0)));
	// } else if (ca.getAccess() == CatalogueAccess.WRITE) {
	//
	// final List<SE> ses = new LinkedList<SE>();
	//
	// if (staticSEs != null)
	// ses.addAll(staticSEs);
	//
	// filterSEs(ses, qos, exxSes);
	//
	// if (ses.size() >= qosCount) {
	// final List<SE> dynamics = SEUtils.getClosestSEs(sitename);
	//
	// filterSEs(dynamics, qos, exxSes);
	//
	// final Iterator<SE> it = dynamics.iterator();
	//
	// while (ses.size() < qosCount && it.hasNext())
	// ses.add(it.next());
	// }
	//
	// getEnvelopesforSEList(ca, ses);
	// }
	// }
	//
	// private static final void filterSEs(final List<SE> ses, final String qos,
	// final Set<SE> exxSes) {
	// final Iterator<SE> it = ses.iterator();
	//
	// while (it.hasNext()) {
	// final SE se = it.next();
	//
	// if (!se.qos.contains(qos) || exxSes.contains(se))
	// it.remove();
	// }
	// }
	//
	// private static void getEnvelopesforSEList(final CatalogueAccess ca,
	// final Collection<SE> ses) {
	// for (final SE se : ses) {
	// ca.addEnvelope(new XrootDEnvelope(ca, se, se.seioDaemons
	// + se.seStoragePath + ca.getGUID().toString()));
	// }
	// }

}
