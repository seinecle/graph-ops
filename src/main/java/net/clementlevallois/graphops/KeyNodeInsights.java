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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.clementlevallois.functions.model.KeyNodesInfo;
import net.clementlevallois.utils.Clock;
import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.plugin.partition.PartitionBuilder.NodePartitionFilter;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.GraphView;
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

public class KeyNodeInsights {

    private GraphModel gm;
    private String sessionId = "";
    private String callbackURL = "";
    private String dataPersistenceId = "";
    private boolean messagesEnabled = false;


    public static void main(String[] args) {

        // Pour réaliser des tests
        Path exampleGexf = Path.of("G:\\Mon Drive\\Personnel et confidential\\Docs Perso Clement\\mes followers twitter 2023\\seinecle.gexf");

        KeyNodeInsights function = new KeyNodeInsights();
        function.loadTestGexf(exampleGexf);
        Integer topNodesPerCommunity = 5;
        Integer minCommunitySize = 15;
        KeyNodesInfo keyNodesInsights = function.analyze("", topNodesPerCommunity, minCommunitySize);

        for (var communityEntry : keyNodesInsights.getInsights().entrySet()) {
            String community = communityEntry.getKey();
            System.out.println("Community: " + community);

            int count = 0;
            for (var insightEntry : communityEntry.getValue().entrySet()) {
                String nodeLabel = keyNodesInsights.getLabelForNodeId(insightEntry.getValue());
                System.out.println("\t" + insightEntry.getKey() + " -> " + nodeLabel);
                count++;
                if(count >= topNodesPerCommunity) break;
            }
        }
    }

    public void setSessionIdAndCallbackURL(String sessionId, String callbackURL, String dataPersistenceId) {
        this.sessionId = sessionId;
        this.callbackURL = callbackURL;
        this.dataPersistenceId = dataPersistenceId;
        messagesEnabled = true;
    }

    public KeyNodesInfo analyze(String userSuppliedCommunityFieldName, int topNodesPerCommunity, int minCommunitySize) {

        if (this.gm == null){
            return new KeyNodesInfo();
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

        return findKeyNodesInsights(modularityColumnName, topNodesPerCommunity, minCommunitySize);
    }

    private KeyNodesInfo findKeyNodesInsights(String modularityColumnName, int topNodesPerCommunity, int minCommunitySize) {


        // Instance pour stocker les insights
        KeyNodesInfo keynodesInfo = new KeyNodesInfo();

        // Récupération du graph global
        
        if (this.gm == null){
            return keynodesInfo;
        }
        
        Graph graph = this.gm.getGraph();


        // =====================================
        // ANALYSE GLOBALE (sans "specialist")
        // =====================================
        keynodesInfo.addCommunity("global");

        
        boolean silent = true;
         Clock clock = new Clock("0", silent);
       // Calculer les centralités globales sur le graph entier
        GraphDistance globalDistance = new GraphDistance();
        globalDistance.setDirected(gm.isDirected());
        globalDistance.setNormalized(true);
        globalDistance.execute(graph);
        clock.closeAndPrintClock();
        
        // Récupérer la colonne contenant la betweenness calculée
        Column globalBetweennessColumn = gm.getNodeTable().getColumn(GraphDistance.BETWEENNESS);
        clock = new Clock("1");
        // Sélection des top nodes par degré via une PriorityQueue
        PriorityQueue<Node> topDegreeNodes = new PriorityQueue<>(topNodesPerCommunity,
                Comparator.comparingInt(n -> graph.getDegree(n)));
        for (Node node : graph.getNodes()) {
            int degree = graph.getDegree(node);
            if (topDegreeNodes.size() < topNodesPerCommunity) {
                topDegreeNodes.offer(node);
            } else if (degree > graph.getDegree(topDegreeNodes.peek())) {
                topDegreeNodes.poll();
                topDegreeNodes.offer(node);
            }
        }
        clock.closeAndPrintClock();
        
        clock = new Clock("2");
        // Extraction et tri en ordre décroissant
        List<Node> sortedByGlobalDegree = new ArrayList<>(topDegreeNodes);
        sortedByGlobalDegree.sort((n1, n2) -> Integer.compare(graph.getDegree(n2), graph.getDegree(n1)));
        for (int i = 0; i < sortedByGlobalDegree.size(); i++) {
            Node node = sortedByGlobalDegree.get(i);
            keynodesInfo.addInsightToCommunity("global", "degree_" + (i + 1),
                    (String) node.getId(), node.getLabel());
        }
        clock.closeAndPrintClock();

        clock = new Clock("3");        
        // Sélection des top nodes par betweenness via une PriorityQueue
        PriorityQueue<Node> topBetweennessNodes = new PriorityQueue<>(topNodesPerCommunity, Comparator.comparingDouble(n -> {
            Double val = (Double) n.getAttribute(globalBetweennessColumn);
            return (val != null ? val : 0.0);
        }));
        for (Node node : graph.getNodes()) {
            Double b = (Double) node.getAttribute(globalBetweennessColumn);
            if (b == null) {
                continue;
            }
            if (topBetweennessNodes.size() < topNodesPerCommunity) {
                topBetweennessNodes.offer(node);
            } else if (b > (Double) topBetweennessNodes.peek().getAttribute(globalBetweennessColumn)) {
                topBetweennessNodes.poll();
                topBetweennessNodes.offer(node);
            }
        }
        clock.closeAndPrintClock();
        
        clock = new Clock("4");          
        // Extraction et tri en ordre décroissant
        List<Node> sortedByGlobalBetweenness = new ArrayList<>(topBetweennessNodes);
        sortedByGlobalBetweenness.sort((n1, n2) -> {
            Double b1 = (Double) n1.getAttribute(globalBetweennessColumn);
            Double b2 = (Double) n2.getAttribute(globalBetweennessColumn);
            return Double.compare(b2, b1);
        });
        for (int i = 0; i < sortedByGlobalBetweenness.size(); i++) {
            Node node = sortedByGlobalBetweenness.get(i);
            keynodesInfo.addInsightToCommunity("global", "betweenness_" + (i + 1),
                    (String) node.getId(), node.getLabel());
        }
        clock.closeAndPrintClock();
        // =====================================
        // ANALYSE PAR COMMUNAUTÉ
        // =====================================
        AppearanceModel appearanceModel = Lookup.getDefault().lookup(AppearanceController.class).getModel();
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);

        // Création du filtre basé sur la partition issue de la colonne de modularité
        NodePartitionFilter partitionFilter = new NodePartitionFilter(appearanceModel,
                appearanceModel.getNodePartition(gm.getNodeTable().getColumn(modularityColumnName)));
        partitionFilter.unselectAll();

        // Récupération de toutes les valeurs (communautés) présentes dans la colonne
        Collection<?> values = partitionFilter.getPartition().getValues(graph);
        for (Object value : values) {
            String community = (String) value;
            keynodesInfo.addCommunity(community);

            // Filtrer le graph pour ne garder que les nodes de la communauté en cours
            partitionFilter.addPart(value);
            Query query = filterController.createQuery(partitionFilter);
            GraphView view = filterController.filter(query);
            gm.setVisibleView(view);
            
            if (gm.getGraphVisible().getNodeCount() < minCommunitySize){
                keynodesInfo.addInsightToCommunity(community, "community is too small to be analyzed");
                continue;
            }

            // Calculer les centralités (betweenness notamment) sur le graph visible
            GraphDistance distance = new GraphDistance();
            distance.setDirected(gm.isDirected());
            distance.setNormalized(true);
            distance.execute(gm.getGraphVisible());
            Column centralityColumn = gm.getNodeTable().getColumn(GraphDistance.BETWEENNESS);

            // Récupération de la liste des nodes de la communauté (graph visible)
            List<Node> visibleNodes = new ArrayList<>();
            for (Node node : gm.getGraphVisible().getNodes()) {
                visibleNodes.add(node);
            }

            // TRI par degré décroissant (local)
            List<Node> sortedByDegree = new ArrayList<>(visibleNodes);
            sortedByDegree.sort((n1, n2) -> Integer.compare(graph.getDegree(n2), graph.getDegree(n1)));
            for (int i = 0; i < Math.min(topNodesPerCommunity, sortedByDegree.size()); i++) {
                Node node = sortedByDegree.get(i);
                keynodesInfo.addInsightToCommunity(community, "degree_" + (i + 1),
                        (String) node.getId(), node.getLabel());
            }

            // TRI par betweenness décroissant (local)
            List<Node> sortedByBetweenness = new ArrayList<>(visibleNodes);
            sortedByBetweenness.sort((n1, n2) -> {
                Double b1 = (Double) n1.getAttribute(centralityColumn);
                Double b2 = (Double) n2.getAttribute(centralityColumn);
                return Double.compare(b2, b1);
            });
            for (int i = 0; i < Math.min(topNodesPerCommunity, sortedByBetweenness.size()); i++) {
                Node node = sortedByBetweenness.get(i);
                keynodesInfo.addInsightToCommunity(community, "betweenness_" + (i + 1),
                        (String) node.getId(), node.getLabel());
            }

            // Pour chaque node de la communauté avec un degré (local) >= 4
            // stocker le degré dans une Map
            Map<String, Integer> localDegree = new HashMap<>();
            for (Node node : visibleNodes) {
                int degree = graph.getDegree(node);
                if (degree >= 4) {
                    localDegree.put((String) node.getId(), degree);
                }
            }

            // On retire le filtre pour repasser sur le graph global
            partitionFilter.unselectAll();

            // Calcul de la "relativeDegreeLocality" : ratio du degré local sur le degré global
            Map<Node, Float> relativeDegreeLocality = new HashMap<>();
            for (var entry : localDegree.entrySet()) {
                String nodeId = entry.getKey();
                Node globalNode = graph.getNode(nodeId);
                if (globalNode != null) {
                    int globalDegree = graph.getDegree(globalNode);
                    if (globalDegree > 0) {
                        float ratio = (float) entry.getValue() / globalDegree;
                        relativeDegreeLocality.put(globalNode, ratio);
                    }
                }
            }

            List<Map.Entry<Node, Float>> sortedRelative = new ArrayList<>(relativeDegreeLocality.entrySet());
            sortedRelative.sort((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()));

            int rank = 1;
            for (var entry : sortedRelative) {
                if (rank > 10) {
                    break;
                }
                keynodesInfo.addInsightToCommunity(community, "specialist_" + rank,
                        (String) entry.getKey().getId(), entry.getKey().getLabel());
                rank++;
            }
        }
        return keynodesInfo;
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
