package uni.dj;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.aether.graph.DependencyNode;


/*
    Internal representation of a dependency node in the tree.
    Stores metadata such as name, version, scope, and whether it was omitted or unused.
 */
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


    /*
        @returns The scope of the dependency (e.g., compile, test).
     */
    public String getScope() {
        return scope;
    }

    /*
        @returns The parent node in the dependency tree.
     */
    public DependencyTreeNode getParent() {
        return parent;
    }

    /*
        @returns The version string of the artifact.
     */
    public String getVersion() {
        return version;
    }

    /*
        @returns The conflict winner description if this node was omitted.
     */
    public String getWinner() {
        return winner;
    }

    /*
        @returns The name of the dependency (groupId.artifactId).
     */
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