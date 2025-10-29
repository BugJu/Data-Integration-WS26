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

import java.io.FileWriter;
import java.io.File;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;

@Mojo(name = "get-dependency-tree", defaultPhase = LifecyclePhase.PACKAGE)
public class GetDependencyTreeMojo extends AbstractMojo {

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
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
    }

    public void buildPluginDependencyTree() {
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
                CollectResult pluginResult = repoSystem.collectDependencies(repoSession, pluginReq);

                // Plugin-Baum ausgeben
                pluginWriter.write("Dependency tree for plugin: " + coords + "\n");
                pluginResult.getRoot().accept(new DependencyTreePrinter(pluginWriter));
                pluginWriter.write("\n");
            }
            getLog().info("Plugin dependency trees written to " + pluginDepFile.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to collect plugin dependencies", e);
        }
    }
}
