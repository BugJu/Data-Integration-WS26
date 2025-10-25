package uni.dj;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

import edu.emory.mathcs.backport.java.util.Collections;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import java.io.FileReader;
import java.io.IOException;

/**
 * Maven Plugin zur Analyse von Projektabhängigkeiten.
 * Wird während der Test-Phase ausgeführt und zählt/analysiert die
 * Dependencies des Projekts.
 */
@Mojo(name = "dependency-counter", defaultPhase = LifecyclePhase.COMPILE)
public class DependencyCounterMojo extends AbstractMojo {
    File m2Repo;

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
        // Initialize m2Repo
        if (settings.getLocalRepository() != null) {
            m2Repo = new File(settings.getLocalRepository());
        } else {
            throw new IllegalStateException("Local repository path is not set in Maven settings.");
        }

        getLog().info("-----------------------------------------------------------------------");
        // Projektmodell-deklarierte pluginRepositories (aus POM: <pluginRepositories>)
        // Laufzeit-/aufgelöste Repositories für Plugins (berücksichtigt Einstellungen,
        // Mirrors etc.)

        List<Dependency> modelDependencies = ((List<?>) project.getDependencies()).stream()
                .map(o -> (Dependency) o)
                .filter(d -> "jar".equals(d.getType()))
                .collect(Collectors.toList());
        List<Plugin> plugins = project.getBuild().getPlugins();
        for (Plugin plugin : plugins) {
            for (Dependency dep : plugin.getDependencies()) {
                modelDependencies.add(dep);
            }
        }
        List<DependencySet> inititalDependencys = buildDependencyFilePath(modelDependencies);
        for (DependencySet dep : inititalDependencys) {
            getLog().info(dep.getFileName() + ", exists: " + dep.file.exists());
        }
        getLog().info(
                "first Pom file: " + inititalDependencys.get(0).getFileName() + " with following dependdencies:");
        List<DependencySet> inititalDependencysTransitive = getTransitiveDependencies(inititalDependencys);
        for (DependencySet transDep : inititalDependencysTransitive.get(0).postDependencies) {
            getLog().info("Dependency POM File: " + transDep.getFileName() + "\n" + "Pre-Dependencies: "
                    + transDep.preDependencies.get(0).getFileName());
        }
        // Extrahiere die einzelnen Teile der groupId und baue einen Pfad
        // Beispiel: groupId "org.springframework" ->F
        // ~/.m2/repository/org/springframework
        // List<Dependency> transitiveDependencies =
        // getTransitiveDependencies(dependencyFilePaths);
        // getLog().info(transitiveDependencies.toString());

        getLog().info("-----------------------------------------------------------------------");
    }

    private List<DependencySet> buildDependencyFilePath(List<Dependency> dependencies) throws IllegalStateException {
        List<DependencySet> initialDeps = new ArrayList<>();
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
                    .append(dep.getVersion()).append("." + "pom").toString();
            filePaths.add(new File(m2Repo.getAbsolutePath() + pathBuilder.toString()));
        }
        getLog().info("Total dependencies processed: " + filePaths.size());
        for (File file : filePaths) {
            DependencySet depSet = new DependencySet();
            depSet.name = file.getName();
            depSet.file = file;
            initialDeps.add(depSet);
        }
        return initialDeps;
    }

    private List<DependencySet> getTransitiveDependencies(List<DependencySet> dependencySets) {
        for (DependencySet depSet : dependencySets) {
            List<Dependency> transitiveDeps = readPomFile(depSet);
            List<DependencySet> transDepSets = buildDependencyFilePath(transitiveDeps);
            for (DependencySet transDepSet : transDepSets) {
                transDepSet.preDependencies.add(depSet);
                depSet.postDependencies.add(transDepSet);
            }
        }
        return dependencySets;
    }

    private List<Dependency> readPomFile(DependencySet depSet) {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (FileReader fileReader = new FileReader(depSet.file)) {
            Model model = reader.read(fileReader);
            List<Dependency> dependencies = model.getDependencies();
            return dependencies;
        } catch (Exception e) {
            getLog().error("Error reading POM file: " + depSet.file.getAbsolutePath(), e);
        }
        return Collections.emptyList();
    }
}