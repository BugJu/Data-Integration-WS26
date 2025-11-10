package uni.dj;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import java.util.ArrayList;
import java.util.List;

public class OmittedDependencyFinder {
    public List<DependencyTreeNode> allNodes = new ArrayList<>();

    public OmittedDependencyFinder(CollectResult result, MavenLogger logger) throws Exception {
        DependencyNode root = result.getRoot();
        if (root != null) {
            traverseTree(root);
            logger.info("Found " + allNodes.size() + " nodes");
            logger.info("Found " + allNodes.get(1).name + " nodes");
            allNodes.get(1).children.forEach(child -> logger.info(child.name + ", is Omitted: " + child.isOmmitted + " winner: " + child.winner));
            List<DependencyTreeNode> depsUsingOmitted = new ArrayList<>();
            List<DependencyTreeNode> depsUsingWinner = new ArrayList<>();
            printOmittedDependencies(allNodes, logger, depsUsingOmitted, depsUsingWinner);
        } else {
            logger.info("abbruch");
        }
    }

    private DependencyTreeNode traverseTree(DependencyNode currentNode) {
        DependencyTreeNode myNode = new DependencyTreeNode();
        myNode.name = currentNode.getDependency().getArtifact() != null ? currentNode.getDependency().getArtifact().getGroupId() + "." + currentNode.getDependency().getArtifact().getArtifactId() : "";
        myNode.version = currentNode.getArtifact() != null ? currentNode.getDependency().getArtifact().getVersion() : "";
        myNode.isOmmitted = currentNode.getDependency() != null && currentNode.getData().get(ConflictResolver.NODE_DATA_WINNER) != null &&
                !(currentNode.getDependency().toString().equals(currentNode.getData().get(ConflictResolver.NODE_DATA_WINNER).toString()));
        myNode.winner = myNode.isOmmitted ? currentNode.getData().get(ConflictResolver.NODE_DATA_WINNER).toString() : null;
        myNode.isLeaf = currentNode.getChildren().isEmpty();
        myNode.scope = currentNode.getDependency().getScope();
        myNode.node = currentNode;
        allNodes.add(myNode);

        for (DependencyNode child : currentNode.getChildren()) {
            DependencyTreeNode childNode = traverseTree(child);
            childNode.parent = myNode;
            myNode.children.add(childNode);
        }
        return myNode;

    }

    private void printOmittedDependencies(List<DependencyTreeNode> nodes, MavenLogger logger, List<DependencyTreeNode> depsUsingOmitted, List<DependencyTreeNode> depsUsingWinner) throws Exception {
        nodes.forEach(originalNode -> {
            if (originalNode.isOmmitted) {
                depsUsingOmitted.clear(); // Liste für jeden Node leeren
                depsUsingWinner.clear();
                DependencyTreeNode node = originalNode;
                depsUsingOmitted.add(node);
                String winnerNodeString = originalNode.getWinner();
                String winnerVersionString = winnerNodeString.substring(winnerNodeString.lastIndexOf(":") + 1, winnerNodeString.indexOf(" "));
                String winnerScope = winnerNodeString.substring(winnerNodeString.indexOf(" ") + 1);
                DependencyTreeNode winnerNode = findOrigNodeInList(winnerVersionString, originalNode, winnerScope, nodes);
                assert winnerNode != null;
                depsUsingWinner.add(winnerNode);

                // Pfad zur Root sammeln
                while (node.parent != null) {
                    depsUsingOmitted.add(node.parent);
                    node = node.parent;
                }

                while (winnerNode.parent != null) {
                    depsUsingWinner.add(winnerNode.parent);
                    winnerNode = winnerNode.parent;
                }
                logger.warn(originalNode.getParent().getName() + " tried using " + originalNode.getName() + " with Version "
                        + originalNode.getVersion() + " and scope " + originalNode.getScope() + " but was omitted due to " + originalNode.getWinner());

                // Pfad in umgekehrter Reihenfolge ausgeben
                for (int i = depsUsingOmitted.size() - 1; i >= 0; i--) {
                    String indent = "  ".repeat(depsUsingOmitted.size() - 1 - i);
                    logger.info(indent + "-->" + depsUsingOmitted.get(i).getName() + " (" + depsUsingOmitted.get(i).getVersion() + ")");
                }
                logger.info("Dependency Path by winner " + depsUsingWinner.get(0).getName() + " (" + depsUsingWinner.get(0).getVersion() + ")");

                for (int i = depsUsingWinner.size() - 1; i >= 0; i--) {
                    String indent = "  ".repeat(depsUsingWinner.size() - 1 - i);
                    logger.info(indent + "-->" + depsUsingWinner.get(i).getName() + " (" + depsUsingWinner.get(i).getVersion() + ")");
                }
            }
        });
    }

    private DependencyTreeNode findOrigNodeInList(String winnerVersion, DependencyTreeNode currNode, String winnerScope, List<DependencyTreeNode> nodes) {
        String nodeName = currNode.getName();
        String winnerScopeStripped = winnerScope.replace("(", "").replace(")", "").replace("?", "").trim();
        for (DependencyTreeNode node : nodes) {
            if (node.getName().equals(nodeName.trim()) && node.getVersion().equals(winnerVersion.trim()) && node.getScope().equals(winnerScopeStripped)) {
                return node;
            }
        }
        return null;
    }
}
