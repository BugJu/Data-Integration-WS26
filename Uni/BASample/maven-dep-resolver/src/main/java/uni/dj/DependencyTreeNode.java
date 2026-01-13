package uni.dj;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.aether.graph.DependencyNode;


public class DependencyTreeNode {
    public DependencyTreeNode parent = null;
    public String name = "";
    public String version = "";
    public Boolean isOmmitted = false;
    public String winner = "";
    public Boolean isLeaf = false;
    public String scope = "";
    public List<DependencyTreeNode> children = new ArrayList<>();
    public DependencyNode node = null;
    public String winnerNodeName = null;
    public String pathToDependencyJar = "";
    public String getPathToDependencyPom = "";
    public String pathToDependency = "";
    public Boolean unused = false;


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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependencyTreeNode that = (DependencyTreeNode) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(version, that.version) &&
                Objects.equals(scope, that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version, scope);
    }
}