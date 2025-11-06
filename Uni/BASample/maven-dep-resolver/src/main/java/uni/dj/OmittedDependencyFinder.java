package uni.dj;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import java.util.ArrayList;
import java.util.List;

public class OmittedDependencyFinder {
    public List<DependencyTreeNode> allNodes = new ArrayList<>();

    public OmittedDependencyFinder(CollectResult result, MavenLogger logger) {
        DependencyNode root = result.getRoot();
        if (root != null) {
            traverseTree(root);
            logger.info("Found " + allNodes.size() + " nodes");
            logger.info("Found " + allNodes.get(1).name + " nodes");
            allNodes.get(1).children.forEach(child -> logger.info(child.name + ", is Omitted: " + child.isOmmitted + " winner: " + child.winner));
            printOmittedDependencies(allNodes, logger);
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
        allNodes.add(myNode);

        for (DependencyNode child : currentNode.getChildren()) {
            DependencyTreeNode childNode = traverseTree(child);
            childNode.parent = myNode;
            myNode.children.add(childNode);
        }
        return myNode;

    }

    private void printOmittedDependencies(List<DependencyTreeNode> nodes, MavenLogger logger) {
        nodes.forEach(node -> {
            if (node.isOmmitted) {
                logger.warn(node.getParent().getName() + " tried using " + node.getName() + " with Version "
                        + node.getVersion() + " and scope " + node.getScope() + " but was omitted " + " due to " + node.getWinner());
            }

        });

    }
}
