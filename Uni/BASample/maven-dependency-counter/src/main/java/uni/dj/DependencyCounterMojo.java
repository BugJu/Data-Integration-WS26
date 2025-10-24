package uni.dj;

import java.io.File;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Maven Plugin zur Analyse von Projektabhängigkeiten.
 * Wird während der Compile-Phase ausgeführt und zählt/analysiert die
 * Dependencies des Projekts.
 */
@Mojo(name = "dependency-counter", defaultPhase = LifecyclePhase.COMPILE)
public class DependencyCounterMojo extends AbstractMojo {

    /**
     * Das aktuelle Maven-Projekt, wird automatisch von Maven injiziert.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

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
    public void execute() {
        getLog().info("-----------------------------------------------------------------------");
        @SuppressWarnings("unchecked")
        List<Dependency> dependencies = ((List<?>) project.getDependencies()).stream()
                .map(o -> (Dependency) o)
                .filter(d -> "jar".equals(d.getType()))
                .collect(Collectors.toList());
        // Extrahiere die ersten Teile der groupIds (bis zum ersten Punkt) und logge die
        // einzigartigen Präfixe
        getLog().info(dependencies.toString());
        List<String> groupPrefixes = dependencies.stream()
                .map(Dependency::getGroupId)
                .map(g -> {
                    int i = g.indexOf('.');
                    return (i != -1) ? g.substring(0, i) : g;
                })
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        getLog().info("Unique groupId prefixes: " + groupPrefixes);

        // Extrahiere die einzelnen Teile der groupId und baue einen Pfad
        // Beispiel: groupId "org.springframework" ->
        // ~/.m2/repository/org/springframework
        List<java.io.File> filePaths = new java.util.ArrayList<>();
        String userHome = System.getProperty("user.home");
        File m2Repo = new File(userHome, ".m2" + File.separator + "repository");

        for (Dependency dep : dependencies) {
            String groupId = dep.getGroupId();
            // split muss den Punkt escapen, da '.' in regex für "jedes Zeichen" steht
            String[] parts = groupId.split("\\.");
            StringBuilder pathBuilder = new StringBuilder();
            for (String part : parts) {
                // Baue Pfadsegmente sicher mit File.separator
                pathBuilder.append(File.separator).append(part);
            }

            pathBuilder.append(File.separator).append(dep.getArtifactId()).append(File.separator)
                    .append(dep.getVersion()).append(File.separator);
            pathBuilder.append(dep.getArtifactId()).append("-")
                    .append(dep.getVersion()).append("." + dep.getType()).toString();
            filePaths.add(new File(m2Repo.getAbsolutePath() + pathBuilder.toString()));
        }

        // Sicherstellen, dass mindestens ein Pfad gefunden wurde bevor darauf
        // zugegriffen wird
        for(int i= 0; i<10; i++) {
          getLog().info(filePaths.get(i).toString() + " check if exists: " + filePaths.get(i).exists());   
        }
        getLog().info("-----------------------------------------------------------------------");
    }
}