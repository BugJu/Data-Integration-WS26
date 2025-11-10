package uni.dj;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.aether.graph.DependencyNode;


public class DependencyTreeNode {
    DependencyTreeNode parent = null;
    String name = "";
    String version = "";
    Boolean isOmmitted = false;
    String winner = "";
    Boolean isLeaf = false;
    String scope = "";
    List<DependencyTreeNode> children = new ArrayList<>();
    DependencyNode node = null;


    public String getScope() {
        return scope;
    }

    public DependencyTreeNode getParent() {
        return parent;
    }

    public String getVersion() {
        return version;
    }

    public String getWinner() {
        return winner;
    }

    public List<DependencyTreeNode> getChildren() {
        return children;
    }

    public String getName() {
        return name;
    }
}
