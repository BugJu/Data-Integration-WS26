package uni.dj;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class OmittedDependencyFinder {
    private final List<DependencyTreeNode> allNodes = new ArrayList<>();

    private final ArtifactPathResolver pathResolver;

    public OmittedDependencyFinder(DependencyNode root, Set<org.apache.maven.artifact.Artifact> unusedArtifacts,
                                   MavenLogger logger, ArtifactPathResolver pathResolver
    ) throws Exception {
        this.pathResolver = pathResolver;
        if (root != null) {
            traverseTree(root, null, unusedArtifacts, logger);
            logger.info("Found " + allNodes.size() + " nodes in total");

            List<DependencyTreeNode> omittedDeps = getOmittedDependencies();
            logger.info("Found " + omittedDeps.size() + " omitted dependencies");

            printOmittedDependencies(omittedDeps, logger);
        } else {
            logger.warn("Root node is null, cannot analyze dependencies");
        }
    }

    private void traverseTree(DependencyNode currentNode, DependencyTreeNode parent, Set<org.apache.maven.artifact.Artifact> unusedArtifacts, MavenLogger logger) {
        DependencyTreeNode myNode = new DependencyTreeNode();

        if (currentNode.getDependency() != null && currentNode.getDependency().getArtifact() != null) {
            myNode.name = currentNode.getDependency().getArtifact().getGroupId() + "."
                    + currentNode.getDependency().getArtifact().getArtifactId();
            myNode.version = currentNode.getDependency().getArtifact().getVersion();
        }

        // Prüfe ob Dependency omitted wurde
        Object winnerObj = currentNode.getData().get(ConflictResolver.NODE_DATA_WINNER);
        if (currentNode.getDependency() != null && winnerObj != null) {
            String depStr = currentNode.getDependency().toString();
            String winStr = winnerObj.toString();
            myNode.isOmmitted = !depStr.equals(winStr);
            if (myNode.isOmmitted) {
                myNode.winner = winStr;
                myNode.winnerNodeName = winStr;
            }
        }


        myNode.isLeaf = currentNode.getChildren().isEmpty();
        myNode.scope = currentNode.getDependency() != null ? currentNode.getDependency().getScope() : "";
        myNode.node = currentNode;
        myNode.parent = parent;
        String[] pathsToJarPom = pathResolver.buildDependencyFilePath(currentNode);
        myNode.pathToDependency = pathsToJarPom[0];
        myNode.pathToDependencyJar = pathsToJarPom[1];
        myNode.getPathToDependencyPom = pathsToJarPom[2];
        unusedArtifacts.forEach(artifact -> {
            String nodeGroupId = currentNode.getArtifact().getGroupId();
            String nodeArtifactId = currentNode.getArtifact().getArtifactId();
            String nodeVersion = currentNode.getArtifact().getVersion();

            // Vergleiche die einzelnen Komponenten
            if (nodeGroupId.equals(artifact.getGroupId()) &&
                    nodeArtifactId.equals(artifact.getArtifactId()) &&
                    nodeVersion.equals(artifact.getVersion())) {
                myNode.unused = true;
            }
        });
        allNodes.add(myNode);

        for (DependencyNode child : currentNode.getChildren()) {
            traverseTree(child, myNode, unusedArtifacts, logger);
        }
    }

    /**
     * Gibt alle omitted dependencies zurück
     */
    public List<DependencyTreeNode> getOmittedDependencies() {
        return allNodes.stream()
                .filter(node -> node.isOmmitted)
                .collect(Collectors.toList());
    }

    /**
     * Gibt alle Nodes zurück (für externe Verarbeitung und JSON-Serialisierung)
     */
    public List<DependencyTreeNode> getAllNodes() {
        return new ArrayList<>(allNodes);
    }

    /**
     * Findet einen Node nach Name und Version
     */
    public DependencyTreeNode findNode(String name, String version, String scope) {
        return allNodes.stream()
                .filter(node -> node.getName().equals(name)
                        && node.getVersion().equals(version)
                        && node.getScope().equals(scope))
                .findFirst()
                .orElse(null);
    }

    private void printOmittedDependencies(List<DependencyTreeNode> omittedNodes, MavenLogger logger) {
        omittedNodes.forEach(omittedNode -> {
            String winnerVersionString = extractVersionFromWinner(omittedNode.getWinner());
            String winnerScope = extractScopeFromWinner(omittedNode.getWinner());

            DependencyTreeNode winnerNode = findNode(omittedNode.getName(), winnerVersionString, winnerScope);

            if (winnerNode != null) {
                logger.warn(omittedNode.getParent().getName() + " tried using "
                        + omittedNode.getName() + " with Version " + omittedNode.getVersion()
                        + " and scope " + omittedNode.getScope() + " but was omitted due to "
                        + omittedNode.getWinner());

                // Pfad zur Root für omitted dependency
                List<DependencyTreeNode> omittedPath = buildPathToRoot(omittedNode);
                for (int i = omittedPath.size() - 1; i >= 0; i--) {
                    String indent = "  ".repeat(omittedPath.size() - 1 - i);
                    logger.info(indent + "-->" + omittedPath.get(i).getName()
                            + " (" + omittedPath.get(i).getVersion() + ")");
                }

                // Pfad zur Root für winner dependency
                logger.info("Dependency Path by winner " + winnerNode.getName()
                        + " (" + winnerNode.getVersion() + ")");
                List<DependencyTreeNode> winnerPath = buildPathToRoot(winnerNode);
                for (int i = winnerPath.size() - 1; i >= 0; i--) {
                    String indent = "  ".repeat(winnerPath.size() - 1 - i);
                    logger.info(indent + "-->" + winnerPath.get(i).getName()
                            + " (" + winnerPath.get(i).getVersion() + ")");
                }
            }
        });
    }

    private List<DependencyTreeNode> buildPathToRoot(DependencyTreeNode node) {
        List<DependencyTreeNode> path = new ArrayList<>();
        DependencyTreeNode current = node;
        while (current != null) {
            path.add(current);
            current = current.getParent();
        }
        return path;
    }

    private String extractVersionFromWinner(String winnerString) {
        if (winnerString == null) return "";
        int lastColon = winnerString.lastIndexOf(":");
        int spaceIndex = winnerString.indexOf(" ", lastColon);
        if (spaceIndex == -1) spaceIndex = winnerString.length();
        return winnerString.substring(lastColon + 1, spaceIndex).trim();
    }

    private String extractScopeFromWinner(String winnerString) {
        if (winnerString == null) return "";
        int spaceIndex = winnerString.indexOf(" ");
        if (spaceIndex == -1) return "";
        return winnerString.substring(spaceIndex + 1)
                .replace("(", "").replace(")", "").replace("?", "").trim();
    }

}