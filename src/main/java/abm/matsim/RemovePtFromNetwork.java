package abm.matsim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.network.io.NetworkWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemovePtFromNetwork {

    public static void main(String[] args) {

        // =====================================================================
        // 1. FILES
        // =====================================================================
        String inputNetwork  = "output/uncalibrated_no_telework_25_pct_population/2011_scenario/network_hbefa.xml";
        String outputNetwork = "output/uncalibrated_no_telework_25_pct_population/2011_scenario/cleaned_network_hbefa.xml";

        // =====================================================================
        // 2. READ NETWORK
        // =====================================================================
        Network network = NetworkUtils.readNetwork(inputNetwork);
        System.out.println("Read network: " + network.getNodes().size() + " nodes, "
                + network.getLinks().size() + " links");

        // =====================================================================
        // 3. COLLECT PT LINKS
        //    a link is PT if its id starts with "pt_" OR it does not allow car
        // =====================================================================
        List<Id<Link>> ptLinks = new ArrayList<>();
        for (Link link : network.getLinks().values()) {
            boolean idIsPt = link.getId().toString().startsWith("pt_");
            boolean noCar = !link.getAllowedModes().contains(TransportMode.car);
            if (idIsPt || noCar) {
                ptLinks.add(link.getId());
            }
        }

        // =====================================================================
        // 4. REMOVE PT LINKS
        // =====================================================================
        for (Id<Link> linkId : ptLinks) {
            network.removeLink(linkId);
        }
        System.out.println("Removed PT links: " + ptLinks.size());

        // =====================================================================
        // 5. COLLECT + REMOVE NODES WITHOUT ANY LINK (former PT stops)
        // =====================================================================
        List<Id<Node>> emptyNodes = new ArrayList<>();
        for (Node node : network.getNodes().values()) {
            if (node.getInLinks().isEmpty() && node.getOutLinks().isEmpty()) {
                emptyNodes.add(node.getId());
            }
        }
        for (Id<Node> nodeId : emptyNodes) {
            network.removeNode(nodeId);
        }
        System.out.println("Removed empty nodes: " + emptyNodes.size());

        // =====================================================================
        // 6. ADD MY MODES BESIDE "car"
        //    modes="car"  ->  modes="car,Car_GASOLINE,Car_DIESEL,truck"
        // =====================================================================
        int roadLinks = 0;
        for (Link link : network.getLinks().values()) {
            if (link.getAllowedModes().contains(TransportMode.car)) {
                Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
                allowedModes.add("Car_GASOLINE");
                allowedModes.add("Car_DIESEL");
                allowedModes.add("truck");
                link.setAllowedModes(allowedModes);
                roadLinks++;
            }
        }
        System.out.println("Modes added to road links: " + roadLinks);

        // =====================================================================
        // 7. CLEAN: every mode fully connected (no dead ends / islands)
        // =====================================================================
        new MultimodalNetworkCleaner(network)
                .run(new HashSet<>(Arrays.asList("car", "Car_GASOLINE", "Car_DIESEL", "truck")));

        // nodes that lost all links during cleaning
        List<Id<Node>> emptyNodesAfterClean = new ArrayList<>();
        for (Node node : network.getNodes().values()) {
            if (node.getInLinks().isEmpty() && node.getOutLinks().isEmpty()) {
                emptyNodesAfterClean.add(node.getId());
            }
        }
        for (Id<Node> nodeId : emptyNodesAfterClean) {
            network.removeNode(nodeId);
        }
        System.out.println("Network cleaned, removed empty nodes after cleaning: " + emptyNodesAfterClean.size());

        // =====================================================================
        // 8. WRITE
        // =====================================================================
        new NetworkWriter(network).write(outputNetwork);
        System.out.println("Written: " + outputNetwork + " (" + network.getNodes().size()
                + " nodes, " + network.getLinks().size() + " links)");
    }
}