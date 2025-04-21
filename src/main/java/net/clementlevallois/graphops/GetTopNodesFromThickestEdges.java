/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.graphops;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.EdgeIterable;
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
import org.openide.util.Lookup;

/**
 *
 * @author LEVALLOIS
 */
public class GetTopNodesFromThickestEdges {

    /**
     * @param args the command line arguments
     */
    Path filePath;
    GraphModel gm;
    Set<Node> nodesToKeep = new HashSet();
    Set<String> nodesInGraph = new HashSet();
    Map<Edge, Double> topWeightEdges;
    InputStream is;
    String gexf;
    private final Object lock = new Object();
    
    public GetTopNodesFromThickestEdges(Path filePath) {
        this.filePath = filePath;
    }

    public GetTopNodesFromThickestEdges(GraphModel gm) {
        this.gm = gm;
    }

    public GetTopNodesFromThickestEdges(String gexf) {
        this.gexf = gexf;
    }

    public GetTopNodesFromThickestEdges(InputStream is) {
        this.is = is;
    }

    public String returnTopNodesAndEdges(int topNodes) throws FileNotFoundException {
        if (gm == null) {
            load();
        }

        JsonObjectBuilder result = Json.createObjectBuilder();
        result.add("nodes", addNodes(topNodes));
        result.add("edges", addEdges());

        String string = writeJsonObjectBuilderToString(result);

        return string;
    }

    private void load() throws FileNotFoundException {
        ProjectController projectController = null;
        Container container = null;

        synchronized (lock) {
            try {
                projectController = Lookup.getDefault().lookup(ProjectController.class);
                GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
                ImportController importController = Lookup.getDefault().lookup(ImportController.class);
                projectController.newProject();
                if (filePath != null) {
                    File file = filePath.toFile();
                    container = importController.importFile(file);
                    container.closeLoader();
                } else if (is != null) {
                    FileImporter fi = new ImporterGEXF();
                    container = importController.importFile(is, fi);
                    container.closeLoader();
                } else if (gexf != null) {
                    FileImporter fi = new ImporterGEXF();
                    container = importController.importFile(new StringReader(gexf), fi);
                    container.closeLoader();
                }
                DefaultProcessor processor = new DefaultProcessor();
                processor.setWorkspace(projectController.getCurrentWorkspace());
                processor.setContainers(new ContainerUnloader[]{container.getUnloader()});
                processor.process();
                gm = graphController.getGraphModel();
            } finally {
                if (projectController != null) {
                    projectController.closeCurrentWorkspace();
                    projectController.closeCurrentProject();
                }
                if (container != null) {
                    container.closeLoader();
                }
            }
        }
    }

    private JsonObjectBuilder addNodes(int topNodes) {
        JsonObjectBuilder nodesObjectBuilder = Json.createObjectBuilder();

        Map<Edge, Double> mapEdgesToTheirWeight = new HashMap();

        EdgeIterable edges = gm.getGraph().getEdges();
        Iterator<Edge> iterator = edges.iterator();
        while (iterator.hasNext()) {
            Edge edge = iterator.next();
            mapEdgesToTheirWeight.put(edge, edge.getWeight());
        }

        topWeightEdges = mapEdgesToTheirWeight.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .limit(topNodes)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        Iterator<Map.Entry<Edge, Double>> iteratorTopWeightEdges = topWeightEdges.entrySet().iterator();

        while (iteratorTopWeightEdges.hasNext() && nodesInGraph.size() < topNodes) {
            Map.Entry<Edge, Double> next = iteratorTopWeightEdges.next();
            nodesInGraph.add((String) next.getKey().getSource().getId());
            nodesInGraph.add((String) next.getKey().getTarget().getId());
            nodesObjectBuilder.add((String) next.getKey().getSource().getId(), "1");
            nodesObjectBuilder.add((String) next.getKey().getTarget().getId(), "1");
        }

        return nodesObjectBuilder;
    }

    private JsonObjectBuilder addEdges() {
        JsonObjectBuilder edgesObjectBuilder = Json.createObjectBuilder();

        for (Map.Entry<Edge, Double> next : topWeightEdges.entrySet()) {
            if (nodesInGraph.contains((String) next.getKey().getSource().getId()) && nodesInGraph.contains((String) next.getKey().getTarget().getId())) {
                edgesObjectBuilder.add((String) next.getKey().getId(), Json.createObjectBuilder()
                        .add("source", (String) next.getKey().getSource().getId())
                        .add("target", ((String) next.getKey().getTarget().getId())));
            }
        }

        return edgesObjectBuilder;
    }

    private String writeJsonObjectBuilderToString(JsonObjectBuilder jsBuilder) {
        Map<String, Boolean> configJsonWriter = new HashMap();
        configJsonWriter.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory writerFactory = Json.createWriterFactory(configJsonWriter);
        Writer writer = new StringWriter();
        writerFactory.createWriter(writer).write(jsBuilder.build());

        String json = writer.toString();

        return json;
    }
}
