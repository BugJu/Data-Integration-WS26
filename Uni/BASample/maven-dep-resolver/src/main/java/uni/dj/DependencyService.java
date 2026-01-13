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

public class DependencyService {

    private final RepositorySystem repoSystem;
    private final RepositorySystemSession repoSession;

    public DependencyService(RepositorySystem repoSystem, RepositorySystemSession repoSession) {
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
    }

    public CollectResult collectDependencies(MavenProject project) throws DependencyCollectionException {
        String coords = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
        DefaultArtifact artifact = new DefaultArtifact(coords);

        CollectRequest request = new CollectRequest();
        request.setRoot(new Dependency(artifact, ""));
        request.setRepositories(project.getRemoteProjectRepositories());

        return repoSystem.collectDependencies(repoSession, request);
    }

    public List<Dependency> getAllDependencies(DependencyNode root) {
        List<Dependency> deps = new ArrayList<>();
        if (root != null) {
            collectRecursively(root, deps, false);
        }
        return deps;
    }

    public List<Dependency> getTransitiveDependencies(DependencyNode root) {
        List<Dependency> transitiveDeps = new ArrayList<>();
        if (root != null) {
            for (DependencyNode directChild : root.getChildren()) {
                collectRecursively(directChild, transitiveDeps, true);
            }
        }
        return transitiveDeps;
    }

    public List<Dependency> getDirectDependencies(DependencyNode root) {
        List<Dependency> directDeps = new ArrayList<>();
        if (root != null) {
            for (DependencyNode node : root.getChildren()) {
                if (node.getDependency() != null) {
                    directDeps.add(node.getDependency());
                }
            }
        }
        return directDeps;
    }

    private void collectRecursively(DependencyNode node, List<Dependency> depList, boolean skipFirst) {
        if (!skipFirst && node.getDependency() != null) {
            depList.add(node.getDependency());
        }
        for (DependencyNode child : node.getChildren()) {
            collectRecursively(child, depList, false);
        }
    }
}
