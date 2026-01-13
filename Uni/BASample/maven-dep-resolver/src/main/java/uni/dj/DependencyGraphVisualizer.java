package uni.dj;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/*
    Visualizes the dependency tree by generating DOT and PNG files using Graphviz.
 */
public class DependencyGraphVisualizer {

    private Graph<String, DefaultEdge> graph;
    private final ObjectMapper mapper = new ObjectMapper();

    /*
        Main entry point for visualization.
        Reads the JSON dependency tree and exports it to DOT and PNG formats.
     */
    public void visualize() throws IOException {
        graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // JSON parsen
        JsonNode rootNode = mapper.readTree(new File("target/dependency-tree.json"));

        // Rekursiv alle Abhängigkeiten hinzufügen
        String rootId = createNodeId(rootNode);
        graph.addVertex(rootId);

        processChildren(rootNode, rootId);


        // Als DOT exportieren
        DOTExporter<String, DefaultEdge> exporter = getStringDefaultEdgeDOTExporter();

        try (FileWriter writer = new FileWriter("target/dependency-tree.dot")) {
            exporter.exportGraph(graph, writer);
        }

        // PNG generieren
        Runtime.getRuntime().exec(new String[]{
                "dot",
                "-Tpng",
                "-Grankdir=TB",  // Top-to-Bottom (vertikal)
                "target/dependency-tree.dot",
                "-o",
                "target/dependency-tree.png"
        });
    }

    /*
        Configures and returns a DOTExporter for the dependency graph.
        @returns Configured DOTExporter instance.
     */
    private static @NotNull DOTExporter<String, DefaultEdge> getStringDefaultEdgeDOTExporter() {
        DOTExporter<String, DefaultEdge> exporter = new DOTExporter<>();
        // Graph-Attribute setzen (vertikal layout)
        exporter.setGraphAttributeProvider(() -> {
            java.util.Map<String, Attribute> attrs = new java.util.LinkedHashMap<>();
            attrs.put("rankdir", DefaultAttribute.createAttribute("TB"));  // Top-to-Bottom
            attrs.put("overlap", DefaultAttribute.createAttribute("false"));
            attrs.put("splines", DefaultAttribute.createAttribute("ortho"));
            return attrs;
        });


        exporter.setVertexAttributeProvider(v -> {
            java.util.Map<String, Attribute> attrs = new java.util.LinkedHashMap<>();
            attrs.put("label", DefaultAttribute.createAttribute(v));
            return attrs;
        });
        return exporter;
    }

    /*
        Recursively processes JSON nodes and adds vertices and edges to the graph.
     */
    private void processChildren(JsonNode node, String parentId) {
        JsonNode children = node.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                String childId = createNodeId(child);
                graph.addVertex(childId);
                graph.addEdge(parentId, childId);

                // Rekursiv weiterverarbeiten
                processChildren(child, childId);
            }
        }
    }

    /*
        Creates a unique identifier for a graph vertex based on artifact metadata.
        @returns Vertex ID string (groupId:artifactId:version).
     */
    private String createNodeId(JsonNode node) {
        String groupId = node.get("groupId").asText("");
        String artifactId = node.get("artifactId").asText("");
        String version = node.get("version").asText("");

        return groupId + ":" + artifactId + ":" + version;
    }
}