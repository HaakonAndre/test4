package alien.soap.services;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lia.util.UUID;

import alien.se.SE;
import alien.se.SEUtils;
import alien.services.AuthenServer;

import alien.catalogue.access.AuthorizationFactory;
import alien.catalogue.access.CatalogueAccess;
import alien.catalogue.access.XrootDEnvelope;

/**
 * @author ron
 * @since Nov 12, 2010
 */
public class SOAPAuthen {

	
	private static final Pattern writeRequest = Pattern.compile("^write");
	private static final Pattern isPerlOption = Pattern.compile("^-");
	private static final Pattern isNumeric = Pattern.compile("^[0-9]+$");
	private static final Pattern SE_NAME = Pattern.compile("^[0-9a-zA-Z_\\-]+(::[0-9a-zA-Z_\\-]+){2}$");
	
	// sub createEnvelope{
	// my $other=shift;
	// my $user=shift;
	// $self->{LOGGER}->set_error_msg();
	// $self->info("$$ Ready to create the envelope for user $user (and @_)");
	//
	// $self->{UI}->execute("user","-", $user);
	//
	// $self->debug(1, "Executing access");
	// my $options=shift;
	// $options.= "v";
	// my (@info)=$self->{UI}->execute("access", $options, @_);
	// $self->info("$$ Everything is done for user $user (and @_)");
	// return @info;
	// }

	// --- for createEnvelope/access the signature will below look like
	// my $user=$self->{CONFIG}->{ROLE};
	// my $options = shift;
	// my $maybeoption = ( shift or 0 );
	// my $access;
	// if ( $maybeoption =~ /^-/ ) {
	// $options .= $maybeoption;
	// $access = (shift or 0),
	// } else {
	// $access = ( $maybeoption or 0);
	// }
	// my $lfns = (shift or 0);
	// my $se = (shift or "");
	// my $size = (shift or "0");
	// my $sesel = (shift or 0);
	// my $extguid = (shift or 0);
	// my $sitename= (shift || 0);
	// my $writeQos = (shift || 0);
	// my $writeQosCount = (shift || 0);
	


	/**
	 * 
	 * createEnvelope - the main entry function of Authen for the SOAP/Perl
	 * interoperability
	 * 
	 * @param user
	 * @param egal
	 * @param envreq
	 * @param lfn
	 * @param staticSEs
	 * @param size
	 * @param noSEs
	 * @param guid
	 * @param site
	 * @param qos
	 * @param qosCount
	 * @return
	 */
//	public Map<String, String>[] createEnvelope(String P_user,
//			String P_options, String P_maybeoptions, String P_lfn,
//			String P_staticSEs, String P_size, String P_sesel_noSEs,
//			String P_guid, String P_sitename, String P_qos, String P_qosCount) {
		
	public String[]	 createEnvelope(String P_user,
				 String P_access, String P_lfn,
				String P_staticSEs, String P_size, 
				String P_guid, String P_md5, String P_sitename, String P_qos, String P_qosCount) {

//		->createEnvelope("sschrein", "", "/alice/cern.ch/user/s/sschrein/testJDLFULL2.jdl", 
//		"", "440", "a00e6c3e-3cbb-11df-8620-0018fe730ae5", "", "CERN","", "1")->result;

		
		
		P_user = ensureStringInitialized(P_user);
//		if (P_user.length() == 0)
//			return replySOAPerrorMessage_access_eof("No username provided");
//		P_options = ensureStringInitialized(P_options);
//		P_maybeoptions = ensureStringInitialized(P_maybeoptions);

		// if ( $maybeoption =~ /^-/ ) {
		// $options .= $maybeoption;
		// $access = (shift or 0),
		// } else {
		// $access = ( $maybeoption or 0);
		// }
//		String P_access = "";
//		String[] someoptions = P_maybeoptions.split(" ");
//		for (String s : someoptions) {
//			if (isPerlOption.matcher(P_access).matches()) {
//				P_options += " " + s;
//			} else {
//				P_access = s;
//			}
//		}
//		if (P_access.length() == 0)
//			return replySOAPerrorMessage_access_eof("No access request provided");


		int access =  CatalogueAccess.INVALID;

		if (P_access != "read") { access = CatalogueAccess.READ; }
		else if (P_access != "delete") { access = CatalogueAccess.DELETE; }
		else if (writeRequest.matcher(P_access).matches()) { access = CatalogueAccess.WRITE;};
//		if(access == CatalogueAccess.INVALID)
//			return replySOAPerrorMessage_access_eof("Illegal access request provided, possible ones are: <read><write-version><write-once><delete>");
		

		P_lfn = ensureStringInitialized(P_lfn);
//		if (P_lfn.length() == 0)
//			return replySOAPerrorMessage_access_eof("No LFN provided");

		P_staticSEs = ensureStringInitialized(P_staticSEs);
		Set<SE> ses = initializeSElist(P_staticSEs);

		P_size = ensureStringInitialized(P_size);
		int size = Integer.valueOf(P_size).intValue(); // here we still need to
		
		
														// ensure that ""
														// becomes 0

//		P_sesel_noSEs = ensureStringInitialized(P_sesel_noSEs);
//		int sesel = 0;
// 
//		if (isNumeric.matcher(P_sesel_noSEs).matches()) {
//			sesel = Integer.valueOf(P_sesel_noSEs).intValue(); // here we still
//																// need to
//																// ensure that
//																// "" becomes 0
//			P_sesel_noSEs = "";
//		}
		
		Set<SE> exxSes = initializeSElist(P_staticSEs);

		P_guid = ensureStringInitialized(P_guid);

		P_sitename = ensureStringInitialized(P_sitename);

		P_qos = ensureStringInitialized(P_qos);

		P_qosCount = ensureStringInitialized(P_qosCount);
		int qosCount = Integer.valueOf(P_qosCount).intValue(); // here we still
																// need to
																// ensure that
																// "" becomes 0

		
		AuthenServer authen = new AuthenServer();

		String[] envelopes =  authen.authorize(P_user,
				access, P_lfn, size, P_guid, ses, exxSes,
				P_qos, qosCount, P_sitename);

		System.out.println("we return over SOAP the string: " +Arrays.toString(envelopes));
		
		return envelopes;
		
	}
	
	
//	public static String access(
//	public static String main(
//	String P_user,
//			String P_options, String P_maybeoptions, String P_lfn,
//			String P_staticSEs, String P_size, String P_sesel_noSEs,
//			String P_guid, String P_sitename, String P_qos, String P_qosCount){
//		
//		SOAPAuthen soap = new SOAPAuthen();
//		
//		Set<XrootDEnvelope> envelopes =   soap.createEnvelope( P_user,
//				 P_options,  P_maybeoptions,  P_lfn,
//				 P_staticSEs,  P_size,  P_sesel_noSEs,
//				 P_guid,  P_sitename, P_qos,  P_qosCount);
//		
//		String ret = "";
////		 for(XrootDEnvelope env: envelopes)
////			 ret += "\n" + env.getPerlEnvelopeTicket().get("envelope") + "\n";
////		 
//		 return ret;
//	}
//	
//
//	public Map<String, String>[] translateEnvelopeIntoMap(
//			Set<XrootDEnvelope> envelope) {
//
//		// foreach envelope call and append String
//		// envelope.getPerlEnvelopeTicket() to return;
//
//		Map<String, String> returnMessage = new HashMap<String, String>();
//		returnMessage.put("error", "AuthenX not implemented yet:");
//		Map<String, String>[] returnAll = new HashMap[1];
//		returnAll[0] = returnMessage;
//		return returnAll;
//
//	}

	/**
	 * 
	 * if the String s is null, set it to ""
	 * 
	 * @param s
	 * @return
	 */
	private String ensureStringInitialized(String s) {
		if (s == null) {
			return "";
		}
		return s;
	}

	/**
	 * 
	 * create a proper PerlAliEN-SOAP reply structure containing an error
	 * message
	 * 
	 * @param message
	 */
	private Map<String, String>[] replySOAPerrorMessage_access_eof(
			String message) {

		Map<String, String> returnMessage = new HashMap<String, String>();
		returnMessage
				.put("error", "AuthenX encountered an error in your request:"
						+ message + ".");
		Map<String, String>[] returnAll = new HashMap[1];
		returnAll[0] = returnMessage;
		return returnAll;
	}



	/**
	 * 
	 * initialize an array from the SE String "se1;se2;se3" containing only the
	 * valid SE names
	 * 
	 * @param sestring
	 * @return array of valid SE names as Strings
	 */
	private Set<SE> initializeSElist(final String sestring) {

		final StringTokenizer st = new StringTokenizer(sestring, ";,");
		
		final Set<SE> ret = new HashSet<SE>();
		
		while (st.hasMoreTokens()){
			final String seName = st.nextToken();
			
			final Matcher m = SE_NAME.matcher(seName);
			
			if (m.matches()){
				SE se = SEUtils.getSE(seName);
				
				if (se!=null)
					ret.add(se);
			}
		}

		return ret;
	}


	//
	// sub doOperation {
	// my $other=shift;
	// my $user=shift;
	// my $directory=shift;
	// my $op=shift;
	// $self->info("$$ Ready to do an operation for $user in $directory (and $op '@_')");
	//
	// $self->{UI}->execute("user","-", $user);
	// my $mydebug=$self->{LOGGER}->getDebugLevel();
	// my $params=[];
	//
	// (my $tracelog,$params) =
	// AliEn::Util::findAndDropArrayElement("-tracelog", @_);
	// $tracelog and $self->{LOGGER}->tracelogOn();
	// (my $debug,$params) = AliEn::Util::getDebugLevelFromParameters(@$params);
	// $debug and $self->{LOGGER}->debugOn($debug);
	// # @_ = @{$params};
	// # $self->info("gron: params for call after cleaning are: @_");
	// $self->{LOGGER}->keepAllMessages();
	// $self->{UI}->{CATALOG}->{DISPPATH}=$directory;
	// my @info;
	// if ($op =~ /authorize/){
	// @info = $self->{UI}->{CATALOG}->authorize(@_);
	// } else {
	// @info = $self->{UI}->execute($op, @_);
	// }
	// my @loglist = @{$self->{LOGGER}->getMessages()};
	//
	// $debug and $self->{LOGGER}->debugOn($mydebug);
	// $self->{LOGGER}->tracelogOff();
	// $self->{LOGGER}->displayMessages();
	// $self->info("$$ doOperation DONE for user $user (and @_)");#, rc = $rc");
	// $self->info("$$ doOperation result: @info".scalar(@info));
	// return { #rc=>$rc,
	// rcvalues=>\@info, rcmessages=>\@loglist};
	// }

	
	
	

//	private Set<String> authorize() {
//	  my $self   = shift;
//	  my $access = (shift || return), my @registerEnvelopes = @_;
//	  my $jid    = pop(@registerEnvelopes);                         # remove the added jobID from Service/Authen
//	  ($jid =~ m/^\d+$/) or push @registerEnvelopes, $jid;
//	  my $options = shift;
//
//	  my $user = $self->{CONFIG}->{ROLE};
//	  $self->{ROLE} and $user = $self->{ROLE};
//
//	  #
//
//	  ($access =~ /^write[\-a-z]*/) and $access = "write";
//	  my $writeReq    = (($access =~ /^write$/)    || 0);
//	  my $mirrorReq   = (($access =~ /^mirror$/)   || 0);
//	  my $readReq     = (($access =~ /^read$/)     || 0);
//	  my $registerReq = (($access =~ /^register$/) || 0);
//	  my $deleteReq   = (($access =~ /^delete$/)   || 0);
//
//	  my $exceptions = 0;
//
//	  ($access =~ /^registerenvs$/)
//	    and return $self->validateSignedEnvAndRegisterAccordingly($user, \@registerEnvelopes);
//
//	  my $lfn      = ($options->{lfn}      || "");
//	  my $wishedSE = ($options->{wishedSE} || "");
//	  my $size        = (($options->{size} and int($options->{size})) || 0);
//	  my $md5         = ($options->{md5}                              || 0);
//	  my $guidRequest = ($options->{guidRequest}                      || 0);
//	  $options->{guid} and $guidRequest = $options->{guid};
//	  my $sitename = ($options->{site}     || 0);
//	  my $writeQos = ($options->{writeQos} || 0);
//	  my $writeQosCount = (($options->{writeQosCount} and int($options->{writeQosCount})) || 0);
//	  my $excludedAndfailedSEs = $self->validateArrayOfSEs(split(/;/, ($options->{excludeSE} || "")));
//	  my $pfn   = ($options->{pfn}   || "");
//	  my $links = ($options->{links} || 0);
//	  my $linksToBeBooked = 1;
//	  my $jobID           = (shift || 0);
//
//	  my $seList = $self->validateArrayOfSEs(split(/;/, $wishedSE));
//
//	  my @returnEnvelopes = ();
//	  my $prepareEnvelope = {};
//
//	  if ($writeReq or $registerReq) {
//	    $prepareEnvelope = $self->getBaseEnvelopeForWriteAccess($user, $lfn, $size, $md5, $guidRequest);
//	    $prepareEnvelope or $self->info("Authorize: Error preparing the envelope for $user and $lfn", 1) and return 0;
//	    if ($registerReq) {
//	      $prepareEnvelope or $self->info("Authorize: Permission denied. Could not register $lfn.", 1) and return 0;
//	      return $self->registerPFNInCatalogue($user, $prepareEnvelope, $pfn, $wishedSE);
//	    }
//	  }
//	  $deleteReq
//	    and ($prepareEnvelope, $seList) = $self->getBaseEnvelopeForDeleteAccess($user, $lfn);
//
//	  $mirrorReq and $prepareEnvelope = $self->getBaseEnvelopeForMirrorAccess($user, $guidRequest, $lfn);
//
//	  ($writeReq or $mirrorReq)
//	    and ($prepareEnvelope, $seList) =
//	    $self->getSEsAndCheckQuotaForWriteOrMirrorAccess($user, $prepareEnvelope, $seList, $sitename, $writeQos,
//	    $writeQosCount, $excludedAndfailedSEs);
//
//	  $readReq
//	    and $prepareEnvelope = $self->getBaseEnvelopeForReadAccess($user, $lfn, $seList, $excludedAndfailedSEs, $sitename)
//	    and @$seList = ($prepareEnvelope->{se});
//	  $prepareEnvelope or $self->info("Authorize: We couldn't create any envelope.", 1) and return 0;
//
//	  ($seList && (scalar(@$seList) gt 0))
//	    or $self->info("Authorize: access: After checkups there's no SE left to make an envelope for.", 1)
//	    and return 0;
//
//	  (scalar(@$seList) lt 0) and $self->info(
//	"Authorize: Authorize: ERROR! There are no SE's after checkups to create an envelope for '$$prepareEnvelope->{lfn}/$prepareEnvelope->{guid}'",
//	    1
//	    )
//	    and return 0;
//
//	  while (scalar(@$seList) gt 0) {
//	    $prepareEnvelope->{se} = shift(@$seList);
//
//	    if ($writeReq or $mirrorReq) {
//	      $prepareEnvelope = $self->calculateXrootdTURLForWriteEnvelope($prepareEnvelope);
//	      $self->addEntryToBookingTableAndOptionalExistingFlagTrigger($user, $prepareEnvelope, $jobID, $mirrorReq) or next;
//	      if ($links and $linksToBeBooked) {
//	        $self->prepookArchiveLinksInBookingTable($user, $jobID, $links, $prepareEnvelope->{guid})
//	          or $self->info("Authorize: The requested links of the archive could not been booked", 1)
//	          and return 0;
//	        $linksToBeBooked = 0;
//	      }
//
//	      # or next;
//	    }
//
//	    $prepareEnvelope->{xurl} = 0;
//
//	    if ( ($prepareEnvelope->{se} =~ /dcache/i)
//	      or ($prepareEnvelope->{se} =~ /alice::((RAL)|(CNAF))::castor/i)
//	      and !($prepareEnvelope->{se} =~ /alice::RAL::castor2_test/i)) {
//	      $prepareEnvelope->{turl} =~ m{^((root)|(file))://([^/]*)/(.*)};
//	      $prepareEnvelope->{xurl} = "root://$4/$prepareEnvelope->{lfn}";
//	    }
//
//	    my $signedEnvelope = $self->signEnvelope($prepareEnvelope, $user);
//
//	    $self->isOldEnvelopeStorageElement($prepareEnvelope->{se})
//	      and $signedEnvelope .= '\&oldEnvelope=' . $self->createAndEncryptEnvelopeTicket($access, $prepareEnvelope);
//
//	    push @returnEnvelopes, $signedEnvelope;
//
//	    if ($self->{MONITOR}) {
//
//	      #my @params= ("$se", $prepareEnvelope->{size});
//	      my $method;
//	      ($access =~ /^((read)|(write))/) and $method = "${1}req";
//	      $access =~ /^delete/ and $method = "delete";
//	      $method
//	        and $self->{MONITOR}->sendParameters("$self->{CONFIG}->{SITE}_QUOTA",
//	        "$self->{ROLE}_$method", ("$signedEnvelope->{se}", $signedEnvelope->{size}));
//	    }
//	  }
//
//	  $self->debug(2, "End of authorize, giving back ENVELOPES: " . Dumper(@returnEnvelopes));
//
//	  return @returnEnvelopes;
//	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
