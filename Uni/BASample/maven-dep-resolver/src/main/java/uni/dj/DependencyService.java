package uni.dj;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

import java.util.ArrayList;
import java.util.List;

/*
    Service for collecting and traversing Maven dependencies using Aether.
 */
public record DependencyService(RepositorySystem repoSystem, RepositorySystemSession repoSession) {

    /*
        Collects all dependencies for a given Maven project.
        @returns CollectResult containing the dependency graph.
     */
    public CollectResult collectDependencies(MavenProject project) throws DependencyCollectionException {
        String coords = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
        DefaultArtifact artifact = new DefaultArtifact(coords);

        CollectRequest request = new CollectRequest();
        request.setRoot(new Dependency(artifact, ""));
        request.setRepositories(project.getRemoteProjectRepositories());

        return repoSystem.collectDependencies(repoSession, request);
    }

    /*
        Flattens the dependency tree into a list of all dependencies.
        @returns List of all dependencies in the tree.
     */
    public List<Dependency> getAllDependencies(DependencyNode root) {
        List<Dependency> deps = new ArrayList<>();
        if (root != null) {
            collectRecursively(root, deps);
        }
        return deps;
    }


    /*
        Recursively traverses the dependency tree and adds dependencies to the list.
     */
    private void collectRecursively(DependencyNode node, List<Dependency> depList) {
        if (node.getDependency() != null) {
            depList.add(node.getDependency());
        }
        for (DependencyNode child : node.getChildren()) {
            collectRecursively(child, depList);
        }
    }
}
