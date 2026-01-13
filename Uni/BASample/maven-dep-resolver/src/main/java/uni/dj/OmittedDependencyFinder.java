package uni.dj;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/*
    Traverses the dependency tree to identify dependencies that were omitted by Maven's conflict resolution.
 */
public class OmittedDependencyFinder {
    private final List<DependencyTreeNode> allNodes = new ArrayList<>();

    private final ArtifactPathResolver pathResolver;

    public OmittedDependencyFinder(DependencyNode root, Set<org.apache.maven.artifact.Artifact> unusedArtifacts,
                                   MavenLogger logger, ArtifactPathResolver pathResolver
    ) {
        this.pathResolver = pathResolver;
        if (root != null) {
            traverseTree(root, null, unusedArtifacts);
            logger.info("Found " + allNodes.size() + " nodes in total");

            List<DependencyTreeNode> omittedDeps = getOmittedDependencies();
            logger.info("Found " + omittedDeps.size() + " omitted dependencies");

            printOmittedDependencies(omittedDeps, logger);
        } else {
            logger.warn("Root node is null, cannot analyze dependencies");
        }
    }

    /*
        Recursively traverses the Aether dependency tree and converts it to internal DependencyTreeNode objects.
     */
    private void traverseTree(DependencyNode currentNode, DependencyTreeNode parent, Set<org.apache.maven.artifact.Artifact> unusedArtifacts) {
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
        String[] pathsToJarPom = pathResolver.buildDependencyFilePath(currentNode.getArtifact());
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
            traverseTree(child, myNode, unusedArtifacts);
        }
    }

    /*
        Returns all dependencies that were omitted due to conflicts.
        @returns List of omitted dependency nodes.
     */
    public List<DependencyTreeNode> getOmittedDependencies() {
        return allNodes.stream()
                .filter(node -> node.isOmmitted)
                .collect(Collectors.toList());
    }

    /*
        Returns all nodes in the dependency tree.
        @returns List of all dependency nodes.
     */
    public List<DependencyTreeNode> getAllNodes() {
        return new ArrayList<>(allNodes);
    }

    /*
        Finds a node by name, version, and scope.
        @returns The matching DependencyTreeNode, or null if not found.
     */
    public DependencyTreeNode findNode(String name, String version, String scope) {
        return allNodes.stream()
                .filter(node -> node.getName().equals(name)
                        && node.getVersion().equals(version)
                        && node.getScope().equals(scope))
                .findFirst()
                .orElse(null);
    }

    /*
        Prints details about omitted dependencies and their paths to the root.
     */
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

    /*
        Builds a path from the given node up to the root.
        @returns List of nodes forming the path to root.
     */
    private List<DependencyTreeNode> buildPathToRoot(DependencyTreeNode node) {
        List<DependencyTreeNode> path = new ArrayList<>();
        DependencyTreeNode current = node;
        while (current != null) {
            path.add(current);
            current = current.getParent();
        }
        return path;
    }

    /*
        Extracts the version string from a conflict winner description.
        @returns The version string.
     */
    private String extractVersionFromWinner(String winnerString) {
        if (winnerString == null) return "";
        int lastColon = winnerString.lastIndexOf(":");
        int spaceIndex = winnerString.indexOf(" ", lastColon);
        if (spaceIndex == -1) spaceIndex = winnerString.length();
        return winnerString.substring(lastColon + 1, spaceIndex).trim();
    }

    /*
        Extracts the scope string from a conflict winner description.
        @returns The scope string.
     */
    private String extractScopeFromWinner(String winnerString) {
        if (winnerString == null) return "";
        int spaceIndex = winnerString.indexOf(" ");
        if (spaceIndex == -1) return "";
        return winnerString.substring(spaceIndex + 1)
                .replace("(", "").replace(")", "").replace("?", "").trim();
    }

}