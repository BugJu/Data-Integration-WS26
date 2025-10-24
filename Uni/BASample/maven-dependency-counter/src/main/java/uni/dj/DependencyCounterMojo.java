package uni.dj;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;




/**
 * Maven Plugin zur Analyse von Projektabhängigkeiten.
 * Wird während der Test-Phase ausgeführt und zählt/analysiert die
 * Dependencies des Projekts.
 */
@Mojo(name = "dependency-counter", defaultPhase = LifecyclePhase.VERIFY)
public class DependencyCounterMojo extends AbstractMojo {

    /**
     * Das aktuelle Maven-Projekt, wird automatisch von Maven injiziert.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Parameter(defaultValue = "${settings}", required = true, readonly = true)
    Settings settings;

    

    /**
     * Optionaler Scope-Parameter, kann verwendet werden um Dependencies zu filtern.
     */
    @Parameter(property = "scope")
    String scope;

    /**
     * Hauptmethode des Plugins, die bei der Ausführung aufgerufen wird.
     * Analysiert die Dependencies des Projekts und gibt verschiedene Informationen
     * aus.
     */

    // Done TODO: Add Plugin Dependencies
    // TODO: Check if only jars are in and how the other work
    public void execute() {
        getLog().info("----------------------------------------------------------------------------------");
        ArrayList<Artifact> artifactList = (ArrayList<Artifact>) project.getArtifacts().stream()
                .collect(Collectors.toCollection(ArrayList::new));

        getLog().info("Total Artifacts Collected: " + artifactList.size());

        // Get all dependencies including transitive ones
        Set<Artifact> artifacts = project.getArtifacts();
        Set<Artifact> dependencies = project.getDependencyArtifacts();
        
       // getLog().info("All Project Artifacts (including transitive): " + (artifacts != null ? artifacts.size() : 0));
       // if (artifacts != null) {
       //     for (Artifact artifact : artifacts) {
       //         getLog().info(artifact.getGroupId() + ":" +
       //                 artifact.getArtifactId() + ":" +
       //                 artifact.getVersion() + " -> " +
       //                 (artifact.getFile() != null ? artifact.getFile().getAbsolutePath() : "No file"));
       //     }
       // }

        getLog().info("-----------------------------------------------------------------------");
        // Projektmodell-deklarierte pluginRepositories (aus POM: <pluginRepositories>)
        // Laufzeit-/aufgelöste Repositories für Plugins (berücksichtigt Einstellungen,
        // Mirrors etc.)
        List<Plugin> plugins = project.getBuild().getPlugins();
        List<Dependency> modelDependencies = ((List<?>) project.getDependencies()).stream()
                .map(o -> (Dependency) o)
                .filter(d -> "jar".equals(d.getType()))
                .collect(Collectors.toList());
        List<File> dependencyFilePaths = buildDependencyFilePath(modelDependencies);
        //for (File dep : dependencyFilePaths) {
        //    getLog().info(dep.toString() + ", exists: " + dep.exists());
        //}
        //getLog().info(settings.getLocalRepository());
        // Extrahiere die einzelnen Teile der groupId und baue einen Pfad
        // Beispiel: groupId "org.springframework" ->F
        // ~/.m2/repository/org/springframework

    }

    private List<File> buildDependencyFilePath(List<Dependency> dependencies) throws IllegalStateException {
        String userHome = System.getProperty("user.home");
        File m2Repo;
        if (settings.getLocalRepository() != null) {
            m2Repo = new File(settings.getLocalRepository());
        } else {
            throw new IllegalStateException("Local repository path is not set in Maven settings.");
        }
        List<java.io.File> filePaths = new java.util.ArrayList<>();
        for (Dependency dep : dependencies) {
            String groupId = dep.getGroupId();
            // split muss den Punkt escapen, da '.' in regex für "jedes Zeichen" steht
            String[] parts = groupId.split("\\.");
            StringBuilder pathBuilder = new StringBuilder();
            for (String part : parts) {
                // Baue Pfadsegmente sicher mit File.separator
                pathBuilder.append(File.separator).append(part);
            }
            // Build paths to the files via home and m2 repository

            pathBuilder.append(File.separator).append(dep.getArtifactId()).append(File.separator)
                    .append(dep.getVersion()).append(File.separator);
            pathBuilder.append(dep.getArtifactId()).append("-")
                    .append(dep.getVersion()).append("." + dep.getType()).toString();
            filePaths.add(new File(m2Repo.getAbsolutePath() + pathBuilder.toString()));
        }
        for (Plugin plugin : project.getBuild().getPlugins()) {

            String groupId = plugin.getGroupId();
            String[] parts = groupId.split("\\.");
            StringBuilder pathBuilder = new StringBuilder();
            for (String part : parts) {
                pathBuilder.append(File.separator).append(part);
            }
            pathBuilder.append(File.separator)
                    .append(plugin.getArtifactId()).append(File.separator)
                    .append(plugin.getVersion()).append(File.separator);
            pathBuilder.append(plugin.getArtifactId()).append("-")
                    .append(plugin.getVersion()).append(".jar");
            File pluginFile = new File(m2Repo.getAbsolutePath() + pathBuilder.toString());
            filePaths.add(pluginFile);
        }
        return filePaths;
    }
}