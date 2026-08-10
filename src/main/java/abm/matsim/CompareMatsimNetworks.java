package abm.matsim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class CompareMatsimNetworks {

    private static final double EPS = 1e-6;

    private static int nodeDifferences = 0;
    private static int linkDifferences = 0;

    private static BufferedWriter writer;

    private static final Set<String> MANUAL_IGNORED_NODE_IDS =
            new HashSet<>(Arrays.asList(
                    "60721074",
                    "60721076"
            ));

    private static final Set<String> MANUAL_IGNORED_LINK_IDS =
            new HashSet<>(Arrays.asList(
                    "339020",
                    "339021",
                    "339030",
                    "339031",
                    "add_1",
                    "add_2"
            ));


    public static void main(String[] args) {

        String QNetwork = "output/10pct-of100population-sample-size/network_pt_road.xml";
        String EmissionNetwork = "output/10pct-of100population-sample-size/network_hbefa - Copy.xml";

        String outputLog = "output/10pct-of100population-sample-size/network_comparison.txt";

        try {
            writer = new BufferedWriter(new FileWriter(outputLog));

            log("MATSim Network Comparison");
            log("Ignoring PT/artificial links, their nodes, and manual exceptions.");
            log("QNetwork        : " + QNetwork);
            log("EmissionNetwork : " + EmissionNetwork);

            Network network1 = NetworkUtils.createNetwork();
            Network network2 = NetworkUtils.createNetwork();

            new MatsimNetworkReader(network1).readFile(QNetwork);
            new MatsimNetworkReader(network2).readFile(EmissionNetwork);

            Set<Id<Link>> ignoredLinks1 = getIgnoredLinks(network1);
            Set<Id<Link>> ignoredLinks2 = getIgnoredLinks(network2);

            Set<Id<Node>> ignoredNodes1 = getNodesOfIgnoredLinks(network1, ignoredLinks1);
            Set<Id<Node>> ignoredNodes2 = getNodesOfIgnoredLinks(network2, ignoredLinks2);

            boolean same = true;

            same &= compareNodes(network1, network2, ignoredNodes1, ignoredNodes2);
            same &= compareLinks(network1, network2, ignoredLinks1, ignoredLinks2);

            log("=======================================================");
            log("SUMMARY");
            log("=======================================================");
            log("Ignored PT/artificial links in network1 = " + ignoredLinks1.size());
            log("Ignored PT/artificial links in network2 = " + ignoredLinks2.size());
            log("Ignored PT/artificial nodes in network1 = " + ignoredNodes1.size());
            log("Ignored PT/artificial nodes in network2 = " + ignoredNodes2.size());
            log("Manually ignored node IDs = " + MANUAL_IGNORED_NODE_IDS);
            log("Manually ignored link IDs = " + MANUAL_IGNORED_LINK_IDS);
            log("Node differences = " + nodeDifferences);
            log("Link differences = " + linkDifferences);

            if (same) {
                log("RESULT: Networks are consistent after ignoring selected PT/artificial/manual exceptions.");
            } else {
                log("RESULT: Networks are NOT consistent.");
            }

            writer.close();

            System.out.println("Comparison finished. Log written to:");
            System.out.println(outputLog);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Set<Id<Link>> getIgnoredLinks(Network network) {

        Set<Id<Link>> ignored = new HashSet<>();

        for (Link link : network.getLinks().values()) {
            if (isArtificialOrPtLink(link) || isManuallyIgnoredLink(link.getId())) {
                ignored.add(link.getId());
            }
        }

        return ignored;
    }

    private static Set<Id<Node>> getNodesOfIgnoredLinks(Network network, Set<Id<Link>> ignoredLinks) {

        Set<Id<Node>> ignoredNodes = new HashSet<>();

        for (Id<Link> linkId : ignoredLinks) {
            Link link = network.getLinks().get(linkId);

            if (link != null) {
                ignoredNodes.add(link.getFromNode().getId());
                ignoredNodes.add(link.getToNode().getId());
            }
        }

        return ignoredNodes;
    }

    private static boolean isArtificialOrPtLink(Link link) {

        String id = link.getId().toString().toLowerCase();

        if (id.contains("pt")) return true;
        if (id.contains("artificial")) return true;
        if (id.contains("transfer")) return true;
        if (id.contains("access")) return true;
        if (id.contains("egress")) return true;
        if (id.contains("tr_")) return true;

        if (link.getAllowedModes() != null) {
            if (link.getAllowedModes().contains("pt")) return true;
            if (link.getAllowedModes().contains("subway")) return true;
            if (link.getAllowedModes().contains("tram")) return true;
            if (link.getAllowedModes().contains("bus")) return true;
            if (link.getAllowedModes().contains("train")) return true;
        }

        return false;
    }

    private static boolean isManuallyIgnoredNode(Id<Node> nodeId) {
        return MANUAL_IGNORED_NODE_IDS.contains(nodeId.toString());
    }

    private static boolean isManuallyIgnoredLink(Id<Link> linkId) {
        return MANUAL_IGNORED_LINK_IDS.contains(linkId.toString());
    }

    private static boolean compareNodes(
            Network n1,
            Network n2,
            Set<Id<Node>> ignoredNodes1,
            Set<Id<Node>> ignoredNodes2) throws IOException {

        boolean same = true;

        Map<Id<Node>, ? extends Node> nodes1 = n1.getNodes();
        Map<Id<Node>, ? extends Node> nodes2 = n2.getNodes();

        for (Id<Node> nodeId : nodes1.keySet()) {

            if (ignoredNodes1.contains(nodeId)
                    || ignoredNodes2.contains(nodeId)
                    || isManuallyIgnoredNode(nodeId)) {
                continue;
            }

            Node node1 = nodes1.get(nodeId);
            Node node2 = nodes2.get(nodeId);

            if (node2 == null) {
                log("Missing non-PT node in network2: " + nodeId);
                same = false;
                nodeDifferences++;
                continue;
            }

            if (!equalsDouble(node1.getCoord().getX(), node2.getCoord().getX())) {
                log("Different node X coordinate: " + nodeId
                        + " | n1=" + node1.getCoord().getX()
                        + " | n2=" + node2.getCoord().getX());
                same = false;
                nodeDifferences++;
            }

            if (!equalsDouble(node1.getCoord().getY(), node2.getCoord().getY())) {
                log("Different node Y coordinate: " + nodeId
                        + " | n1=" + node1.getCoord().getY()
                        + " | n2=" + node2.getCoord().getY());
                same = false;
                nodeDifferences++;
            }
        }

        for (Id<Node> nodeId : nodes2.keySet()) {

            if (ignoredNodes1.contains(nodeId)
                    || ignoredNodes2.contains(nodeId)
                    || isManuallyIgnoredNode(nodeId)) {
                continue;
            }

            if (!nodes1.containsKey(nodeId)) {
                log("Extra non-PT node in network2: " + nodeId);
                same = false;
                nodeDifferences++;
            }
        }

        return same;
    }

    private static boolean compareLinks(
            Network n1,
            Network n2,
            Set<Id<Link>> ignoredLinks1,
            Set<Id<Link>> ignoredLinks2) throws IOException {

        boolean same = true;

        Map<Id<Link>, ? extends Link> links1 = n1.getLinks();
        Map<Id<Link>, ? extends Link> links2 = n2.getLinks();

        for (Id<Link> linkId : links1.keySet()) {

            if (ignoredLinks1.contains(linkId)
                    || ignoredLinks2.contains(linkId)
                    || isManuallyIgnoredLink(linkId)) {
                continue;
            }

            Link link1 = links1.get(linkId);
            Link link2 = links2.get(linkId);

            if (link2 == null) {
                log("Missing non-PT link in network2: " + linkId);
                same = false;
                linkDifferences++;
                continue;
            }

            if (!link1.getFromNode().getId().equals(link2.getFromNode().getId())) {
                log("Different FROM node for link: " + linkId
                        + " | n1=" + link1.getFromNode().getId()
                        + " | n2=" + link2.getFromNode().getId());
                same = false;
                linkDifferences++;
            }

            if (!link1.getToNode().getId().equals(link2.getToNode().getId())) {
                log("Different TO node for link: " + linkId
                        + " | n1=" + link1.getToNode().getId()
                        + " | n2=" + link2.getToNode().getId());
                same = false;
                linkDifferences++;
            }

            if (!equalsDouble(link1.getLength(), link2.getLength())) {
                log("Different length for link: " + linkId
                        + " | n1=" + link1.getLength()
                        + " | n2=" + link2.getLength());
                same = false;
                linkDifferences++;
            }

            if (!equalsDouble(link1.getFreespeed(), link2.getFreespeed())) {
                log("Different freespeed for link: " + linkId
                        + " | n1=" + link1.getFreespeed()
                        + " | n2=" + link2.getFreespeed());
                same = false;
                linkDifferences++;
            }

            if (!equalsDouble(link1.getCapacity(), link2.getCapacity())) {
                log("Different capacity for link: " + linkId
                        + " | n1=" + link1.getCapacity()
                        + " | n2=" + link2.getCapacity());
                same = false;
                linkDifferences++;
            }

            if (!equalsDouble(link1.getNumberOfLanes(), link2.getNumberOfLanes())) {
                log("Different lanes for link: " + linkId
                        + " | n1=" + link1.getNumberOfLanes()
                        + " | n2=" + link2.getNumberOfLanes());
                same = false;
                linkDifferences++;
            }
        }

        for (Id<Link> linkId : links2.keySet()) {

            if (ignoredLinks1.contains(linkId)
                    || ignoredLinks2.contains(linkId)
                    || isManuallyIgnoredLink(linkId)) {
                continue;
            }

            if (!links1.containsKey(linkId)) {
                log("Extra non-PT link in network2: " + linkId);
                same = false;
                linkDifferences++;
            }
        }

        return same;
    }

    private static boolean equalsDouble(double a, double b) {
        return Math.abs(a - b) < EPS;
    }

    private static void log(String text) throws IOException {
        System.out.println(text);
        writer.write(text);
        writer.newLine();
    }
}