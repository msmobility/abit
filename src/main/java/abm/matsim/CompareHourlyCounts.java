package abm.matsim;

import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CompareHourlyCounts {

    // ============================================================
    // HARDCODE PATHS
    // ============================================================

    private static final String EVENTS_FILE =
            "output/validation-25pct-of100population-sample-size/baseline_100Iter_matsim_output/output_events.xml.gz";

    private static final String COUNTS_FILE =
            "output/validation-25pct-of100population-sample-size/matsim_traffic_counts_final.xml";

    private static final String OUTPUT_CSV =
            "output/validation-25pct-of100population-sample-size/hourly_count_comparison.csv";

    private static final double MATSIM_SAMPLE_SIZE = 25.0;

    private static final double SCALE_FACTOR =
            100.0 / MATSIM_SAMPLE_SIZE;

    // ============================================================

    public static void main(String[] args) throws Exception {

        Map<String, double[]> observedCounts =
                readCountsXml(COUNTS_FILE);

        Set<String> detectorLinkIds = observedCounts.keySet();

        HourlyLinkCounter handler =
                new HourlyLinkCounter(detectorLinkIds);

        EventsManager events = EventsUtils.createEventsManager();
        events.addHandler(handler);

        new MatsimEventsReader(events).readFile(EVENTS_FILE);

        writeComparisonCsv(
                OUTPUT_CSV,
                observedCounts,
                handler.simulatedCounts
        );

        System.out.println("Finished.");
        System.out.println("MATSim sample percent: " + MATSIM_SAMPLE_SIZE);
        System.out.println("Applied scale factor: " + SCALE_FACTOR);
        System.out.println("Output CSV: " + OUTPUT_CSV);
    }

    private static Map<String, double[]> readCountsXml(String countsFile)
            throws Exception {

        Map<String, double[]> counts = new LinkedHashMap<>();

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File(countsFile));

        NodeList countNodes = doc.getElementsByTagName("count");

        for (int i = 0; i < countNodes.getLength(); i++) {

            Element countEl = (Element) countNodes.item(i);
            String locId = countEl.getAttribute("loc_id");

            double[] hourly = new double[24];

            NodeList volumes =
                    countEl.getElementsByTagName("volume");

            for (int j = 0; j < volumes.getLength(); j++) {

                Element volEl = (Element) volumes.item(j);

                int hour = Integer.parseInt(
                        volEl.getAttribute("h")
                );

                double value = Double.parseDouble(
                        volEl.getAttribute("val")
                );

                hourly[hour - 1] = value;
            }

            counts.put(locId, hourly);
        }

        return counts;
    }

    private static class HourlyLinkCounter
            implements LinkEnterEventHandler {

        private final Set<String> detectorLinkIds;

        private final Map<String, int[]> simulatedCounts =
                new LinkedHashMap<>();

        HourlyLinkCounter(Set<String> detectorLinkIds) {

            this.detectorLinkIds = detectorLinkIds;

            for (String linkId : detectorLinkIds) {
                simulatedCounts.put(linkId, new int[24]);
            }
        }

        @Override
        public void handleEvent(LinkEnterEvent event) {

            String linkId = event.getLinkId().toString();

            if (!detectorLinkIds.contains(linkId)) {
                return;
            }

            double time = event.getTime();

            if (time < 0 || time >= 24 * 3600) {
                return;
            }

            int hourIndex = (int) (time / 3600);

            simulatedCounts.get(linkId)[hourIndex]++;
        }

        @Override
        public void reset(int iteration) {
            for (int[] values : simulatedCounts.values()) {
                Arrays.fill(values, 0);
            }
        }
    }

    private static void writeComparisonCsv(
            String outputCsv,
            Map<String, double[]> observedCounts,
            Map<String, int[]> simulatedCounts
    ) throws IOException {

        Files.createDirectories(Paths.get(outputCsv).getParent());

        try (BufferedWriter writer =
                     Files.newBufferedWriter(Paths.get(outputCsv))) {

            writer.write(
                    "linkId,hour,observed,simulated_raw," +
                            "simulated_scaled,difference," +
                            "absolute_error,relative_error_percent"
            );
            writer.newLine();

            for (String linkId : observedCounts.keySet()) {

                double[] observed = observedCounts.get(linkId);
                int[] simulated =
                        simulatedCounts.getOrDefault(linkId, new int[24]);

                for (int h = 1; h <= 24; h++) {

                    double obs = observed[h - 1];
                    double simRaw = simulated[h - 1];
                    double simScaled = simRaw * SCALE_FACTOR;

                    double diff = simScaled - obs;
                    double absError = Math.abs(diff);

                    double relError =
                            obs == 0 ? Double.NaN : diff / obs * 100.0;

                    writer.write(String.format(
                            Locale.US,
                            "%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",
                            linkId,
                            h,
                            obs,
                            simRaw,
                            simScaled,
                            diff,
                            absError,
                            relError
                    ));

                    writer.newLine();
                }
            }
        }
    }
}