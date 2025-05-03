/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */
package net.clementlevallois.graphops;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ContainerUnloader;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.plugin.file.ImporterGEXF;
import org.gephi.io.importer.spi.FileImporter;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.statistics.plugin.GraphDistance;
import org.gephi.statistics.plugin.Modularity;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

public class TextSampleFromCommunities {

    private GraphModel gm;

    public static void main(String[] args) {

        // Pour réaliser des tests
        Path exampleGexf = Path.of("G:\\Mon Drive\\Personnel et confidential\\Docs Perso Clement\\mes followers twitter 2023\\seinecle.gexf");

        TextSampleFromCommunities function = new TextSampleFromCommunities();
        function.loadTestGexf(exampleGexf);
        Integer topNodesPerCommunity = 5;
        Integer minCommunitySize = 15;
        Integer maxTotalChars = 1000;
        Map<String, String> textPerCommunity = function.analyze("", "", topNodesPerCommunity, minCommunitySize, maxTotalChars);

        for (var communityEntry : textPerCommunity.entrySet()) {
            String community = communityEntry.getKey();
            System.out.println("Community: " + community);
            System.out.println("Text: " + communityEntry.getValue());

        }
    }

    public Map<String, String> analyze(String userSuppliedCommunityFieldName,
            String textualAttribute,
            int topNodesPerCommunity,
            int minCommunitySize,
            int maxTotalChars
    ) {

        if (this.gm == null) {
            return new HashMap();
        }

        // Calcul des communautés Louvain si le champ n'existe pas
        if ((userSuppliedCommunityFieldName == null || userSuppliedCommunityFieldName.isBlank())
                && !gm.getNodeTable().hasColumn(Modularity.MODULARITY_CLASS)) {
            Modularity modularity = new Modularity();
            modularity.setUseWeight(true);
            modularity.setRandom(false);
            modularity.setResolution(1.0);
            modularity.execute(gm.getGraph());
        }

        String modularityColumnName;
        if (userSuppliedCommunityFieldName != null && !userSuppliedCommunityFieldName.isBlank()) {
            modularityColumnName = userSuppliedCommunityFieldName;
        } else {
            modularityColumnName = Modularity.MODULARITY_CLASS;
        }

        // Calcul des centralités si la colonne est absente
        if (!gm.getNodeTable().hasColumn(GraphDistance.BETWEENNESS)) {
            var distance = new GraphDistance();
            distance.setDirected(gm.isDirected());
            distance.setNormalized(true);
            distance.execute(gm.getGraph());
        }

        return extractTextsByCommunity(modularityColumnName, textualAttribute, topNodesPerCommunity, minCommunitySize, maxTotalChars);
    }

    public Map<String, String> extractTextsByCommunity(String modularityClassColumnName, String textualAttribute, int topNodesPerCommunity, int minCommunitySize, int maxTotalChars) {
        if (gm == null || textualAttribute == null || textualAttribute.isBlank()) {
            return Map.of();
        }

        Graph graph = gm.getGraph();
        Column communityCol = gm.getNodeTable().getColumn(modularityClassColumnName);
        Column textCol = gm.getNodeTable().getColumn(textualAttribute);
        Column betweennessCol = gm.getNodeTable().getColumn(GraphDistance.BETWEENNESS);

        if (communityCol == null || textCol == null || betweennessCol == null) {
            return Map.of();
        }

        // Group nodes by community
        Map<String, List<Node>> nodesByCommunity = new HashMap<>();
        for (Node node : graph.getNodes()) {
            var community = String.valueOf(node.getAttribute(communityCol));
            nodesByCommunity.computeIfAbsent(community, k -> new ArrayList<>()).add(node);
        }

        // Result map: community -> concatenated texts of top nodes
        Map<String, String> communityTexts = new HashMap<>();
        int totalCharsUsed = 0;

        for (var entry : nodesByCommunity.entrySet()) {
            if (totalCharsUsed > maxTotalChars) {
                break;
            }
            var community = entry.getKey();
            var nodes = entry.getValue();

            if (nodes.size() < minCommunitySize) {
                continue;
            }

            // Sort by betweenness descending
            nodes.sort((n1, n2) -> {
                var b1 = (Double) n1.getAttribute(betweennessCol);
                var b2 = (Double) n2.getAttribute(betweennessCol);
                return Double.compare(b2 != null ? b2 : 0.0, b1 != null ? b1 : 0.0);
            });

            var topNodes = nodes.stream().limit(topNodesPerCommunity).toList();

            StringBuilder textBuilder = new StringBuilder();

            for (Node node : topNodes) {
                if (totalCharsUsed >= maxTotalChars) {
                    break;
                }

                String text = (String) node.getAttribute(textCol);
                if (text == null || text.isBlank()) {
                    continue;
                }

                int remaining = maxTotalChars - totalCharsUsed;
                if (text.length() > remaining) {
                    text = text.substring(0, remaining);
                }

                if (!textBuilder.isEmpty()) {
                    textBuilder.append(" ");
                    totalCharsUsed++;
                }

                textBuilder.append(text);
                totalCharsUsed += text.length();
            }

            communityTexts.put(community, textBuilder.toString());
        }

        return communityTexts;
    }

    public void importGexfAsGraph(String gexf) {

        ProjectController pc = null;
        Container container = null;
        try {
            pc = Lookup.getDefault().lookup(ProjectController.class);
            Workspace workspace = pc.newWorkspace(pc.newProject());

            // Get controllers and models
            ImportController importController = Lookup.getDefault().lookup(ImportController.class);

            // Import file
            FileImporter fi = new ImporterGEXF();
            InputStream is = new ByteArrayInputStream(gexf.getBytes());
            container = importController.importFile(is, fi);
            container.closeLoader();
            DefaultProcessor processor = new DefaultProcessor();

            processor.setWorkspace(workspace);
            processor.setContainers(new ContainerUnloader[]{container.getUnloader()});
            processor.process();

            this.gm = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);
        } finally {
            if (pc != null) {
                pc.closeCurrentWorkspace();
                pc.closeCurrentProject();
            }
            if (container != null) {
                container.closeLoader();
            }
        }
    }

    private void loadTestGexf(Path exampleGexf) {
        try {
            String gexfFileAsString = Files.readString(exampleGexf, StandardCharsets.UTF_8);

            // Init a project - and therefore a workspace
            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
            pc.newProject();

            // Get controllers and models
            ImportController importController = Lookup.getDefault().lookup(ImportController.class);

            // Import file
            Container container;
            FileImporter fi = new ImporterGEXF();
            container = importController.importFile(new StringReader(gexfFileAsString), fi);
            container.closeLoader();

            DefaultProcessor processor = new DefaultProcessor();
            processor.setWorkspace(pc.getCurrentWorkspace());
            processor.setContainers(new ContainerUnloader[]{container.getUnloader()});
            processor.process();

            GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
            this.gm = graphController.getGraphModel();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
