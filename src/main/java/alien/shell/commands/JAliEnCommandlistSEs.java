package alien.shell.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import alien.se.SE;
import alien.shell.ShellColor;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lazyj.Format;

/**
 * @author costing
 *
 */
public class JAliEnCommandlistSEs extends JAliEnBaseCommand {
	private List<String> sesToQuery = new ArrayList<>();
	private final Set<String> requestQos = new HashSet<>();

	@Override
	public void run() {
		final List<SE> results = commander.c_api.getSEs(sesToQuery);

		Collections.sort(results);

		final List<SE> filteredSEs = new ArrayList<>(results.size());

		int maxQosLength = 0;
		int maxSENameLength = 0;

		for (final SE se : results) {
			if (!se.seName.contains("::"))
				continue;

			if (!se.qos.containsAll(requestQos))
				continue;

			int qosLen = 0;

			for (final String q : se.qos)
				qosLen += q.length();

			if (se.qos.size() > 1)
				qosLen += (se.qos.size() - 1) * 2;

			maxQosLength = Math.max(maxQosLength, qosLen);

			maxSENameLength = Math.max(maxSENameLength, se.seName.length());

			filteredSEs.add(se);
		}

		commander.printOutln(padRight(" ", maxSENameLength) + "\t\t                Capacity\t  \t\t\tDemote");
		commander.printOutln(padLeft("SE name", maxSENameLength) + "\t ID\t   Total  \t    Used  \t    Free  \t   Read   Write\t" + padRight("QoS", maxQosLength) + "\t  Endpoint URL");

		for (final SE se : filteredSEs) {
			String qos = "";

			int len = 0;

			for (final String q : se.qos) {
				if (qos.length() > 0) {
					len += 2;
					qos += ", ";
				}

				len += q.length();

				switch (q) {
				case "disk":
					qos += ShellColor.jobStateGreen() + q + ShellColor.reset();
					break;
				case "tape":
					qos += ShellColor.jobStateBlue() + q + ShellColor.reset();
					break;
				case "legooutput":
				case "legoinput":
					qos += ShellColor.jobStateYellow() + q + ShellColor.reset();
					break;
				default:
					qos += ShellColor.jobStateRed() + q + ShellColor.reset();
				}
			}

			for (; len < maxQosLength; len++)
				qos += " ";

			final long totalSpace = se.size * 1024;
			final long usedSpace = se.seUsedSpace;
			final long freeSpace = usedSpace <= totalSpace ? totalSpace - usedSpace : 0;

			commander.printOutln(String.format("%1$" + maxSENameLength + "s", se.originalName) + "\t" + String.format("%3d", Integer.valueOf(se.seNumber)) + "\t" + padLeft(Format.size(totalSpace), 8)
					+ "\t" + padLeft(Format.size(usedSpace), 8) + "\t" + padLeft(Format.size(freeSpace), 8) + "\t" + String.format("% .4f", Double.valueOf(se.demoteRead)) + " "
					+ String.format("% .4f", Double.valueOf(se.demoteWrite)) + "\t" + qos + "\t  " + se.generateProtocol());
		}

		commander.printOutln();
	}

	@Override
	public void printHelp() {
		commander.printOutln();
		commander.printOutln("listSEs: print all (or a subset) of the defined SEs with their details");
		commander.printOutln(helpUsage("listSEs", "[-qos filter,by,qos] [SE name] [SE name] ..."));
		commander.printOutln(helpStartOptions());
		commander.printOutln(helpOption("-qos", "filter the SEs by the given QoS classes"));
	}

	@Override
	public boolean canRunWithoutArguments() {
		return true;
	}

	/**
	 * @param commander
	 * @param alArguments
	 * @throws OptionException
	 */
	public JAliEnCommandlistSEs(final JAliEnCOMMander commander, final List<String> alArguments) throws OptionException {
		super(commander, alArguments);

		final OptionParser parser = new OptionParser();
		parser.accepts("qos").withRequiredArg();

		final OptionSet options = parser.parse(alArguments.toArray(new String[] {}));

		if (options.has("qos")) {
			final StringTokenizer st = new StringTokenizer(options.valueOf("qos").toString(), " ,;");

			while (st.hasMoreTokens())
				requestQos.add(st.nextToken());
		}

		sesToQuery = optionToString(options.nonOptionArguments());
	}
}
