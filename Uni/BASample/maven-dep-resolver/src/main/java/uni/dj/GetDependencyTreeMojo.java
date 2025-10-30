package uni.dj;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import uni.dj.OpalSecurityAnalyzer;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;

@Mojo(name = "get-dependency-tree", defaultPhase = LifecyclePhase.COMPILE)
public class GetDependencyTreeMojo extends AbstractMojo {

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${settings}", required = true, readonly = true)
    Settings settings;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        File m2Repo;
        if (settings.getLocalRepository() != null) {
            m2Repo = new File(settings.getLocalRepository());
        } else {
            throw new IllegalStateException("Local repository path is not set in Maven settings.");
        }
        try {
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            String coords = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
            DefaultArtifact artifact = new DefaultArtifact(coords);

            CollectRequest request = new CollectRequest();
            request.setRoot(new Dependency(artifact, ""));
            request.setRepositories(project.getRemoteProjectRepositories());

            CollectResult result = repoSystem.collectDependencies(repoSession, request);

            // Beispiel: Schreibe Baum als String in Datei
            File outputFile = new File(outputDirectory, "dependency-tree.txt");
            try (FileWriter writer = new FileWriter(outputFile)) {
                result.getRoot().accept(new DependencyTreePrinter(writer));
            }
            getLog().info("Dependency tree written to " + outputFile.getAbsolutePath());
            buildPluginDependencyTree();

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to collect dependencies", e);
        }
        List<Dependency> dependencies = buildPluginDependencyTree();
        List<File> depsWithFilePaths = buildDependencyFilePath(dependencies, m2Repo);
        getLog().info(depsWithFilePaths.get(0).toString());
        for (int i = 0; i < 10; i++) {
            String analysisResult = OpalSecurityAnalyzer.analyzeJar(depsWithFilePaths.get(i));
            getLog().info("Analysis Result: " + analysisResult);
        }
    }

    public List<Dependency> buildPluginDependencyTree() {
        CollectResult pluginResult = null;
        try {
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }
        } catch (Exception e) {
            getLog().error("Error building plugin dependency tree", e);
        }
        File pluginDepFile = new File(outputDirectory, "plugin-dependency-tree.txt");
        try (FileWriter pluginWriter = new FileWriter(pluginDepFile)) {
            for (Plugin plugin : project.getBuild().getPlugins()) {
                // Plugin-Koordinaten
                String groupId = plugin.getGroupId();
                String artifactId = plugin.getArtifactId();
                String version = plugin.getVersion();

                if (groupId == null || version == null) {
                    getLog().warn("Skipping plugin (missing coordinates): " + plugin);
                    continue;
                }
                String coords = groupId + ":" + artifactId + ":" + version;
                DefaultArtifact pluginArtifact = new DefaultArtifact(coords);

                // Auflösungsanfrage aufbauen (nutze Plugin-Repos!)
                CollectRequest pluginReq = new CollectRequest();
                pluginReq.setRoot(new Dependency(pluginArtifact, ""));
                pluginReq.setRepositories(project.getRemotePluginRepositories());

                // Transitiven Baum für das Plugin holen
                pluginResult = repoSystem.collectDependencies(repoSession, pluginReq);

                // Plugin-Baum ausgeben
                pluginWriter.write("Dependency tree for plugin: " + coords + "\n");
                pluginResult.getRoot().accept(new DependencyTreePrinter(pluginWriter));
                pluginWriter.write("\n");
            }
            getLog().info("Plugin dependency trees written to " + pluginDepFile.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to collect plugin dependencies", e);
        }
        return collectDependenciesAsList(pluginResult != null ? pluginResult.getRoot() : null);
    }

    public List<Dependency> collectDependenciesAsList(DependencyNode node) {
        List<Dependency> deps = new ArrayList<>();
        collectRecursively(node, deps);
        return deps;
    }

    private void collectRecursively(DependencyNode node, List<Dependency> depList) {
        if (node.getDependency() != null) {
            depList.add(node.getDependency());
        }
        for (DependencyNode child : node.getChildren()) {
            collectRecursively(child, depList);
        }
    }

    private List<File> buildDependencyFilePath(List<Dependency> dependencies, File m2Repo) {
        List<File> filePaths = new ArrayList<>();
        for (Dependency dep : dependencies) {
            String groupId = dep.getArtifact().getGroupId();
            String[] parts = groupId.split("\\.");
            StringBuilder pathBuilder = new StringBuilder();
            for (String part : parts) {
                pathBuilder.append(File.separator).append(part);
            }
            pathBuilder.append(File.separator).append(dep.getArtifact().getArtifactId()).append(File.separator)
                    .append(dep.getArtifact().getVersion()).append(File.separator);
            pathBuilder.append(dep.getArtifact().getArtifactId()).append("-")
                    .append(dep.getArtifact().getVersion()).append(".jar");
            File depFile = new File(m2Repo, pathBuilder.toString());
            filePaths.add(depFile);
        }
        getLog().info("Total dependencies processed: " + filePaths.size());
        return filePaths;
    }

}