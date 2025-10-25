package uni.dj;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
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
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

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
    // TODO: Handle missing POM files
    // TODO: Figure out how files are loaded (versionung and stuff)
    // TODO: Fix that some Versions are hardcoded under POM <version>...</version>
    // Tag, instead are variables like ${project.version}
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
        for (int i = 0; i < filePaths.size(); i++) {
            File file = filePaths.get(i);
            Dependency dep = dependencies.get(i);
            DependencySet depSet = new DependencySet();
            depSet.name = file.getName();
            depSet.file = file;
            depSet.groupId = dep.getGroupId();
            depSet.artifactId = dep.getArtifactId();
            depSet.version = dep.getVersion();
            initialDeps.add(depSet);
        }
        return initialDeps;
    }

    private List<DependencySet> getTransitiveDependencies(List<DependencySet> dependencySets) {
        // recursive collection with cycle detection
        Set<String> visited = new HashSet<>();
        for (DependencySet depSet : dependencySets) {
            collectTransitive(depSet, visited);
        }
        return dependencySets;
    }

    private void collectTransitive(DependencySet root, Set<String> visited) {
        String rootCoord = root.getCoordinate();
        if (rootCoord != null && visited.contains(rootCoord)) {
            return; // already processed
        }
        if (rootCoord != null)
            visited.add(rootCoord);

        List<Dependency> transitiveDeps = readPomFile(root);
        if (transitiveDeps == null || transitiveDeps.isEmpty())
            return;

        List<DependencySet> childSets = buildDependencyFilePath(transitiveDeps);
        for (DependencySet child : childSets) {
            String childCoord = child.getCoordinate();
            // link parent/child
            child.preDependencies.add(root);
            root.postDependencies.add(child);

            // if child not visited, recurse
            if (childCoord == null || !visited.contains(childCoord)) {
                collectTransitive(child, visited);
            }
        }
    }

    private List<Dependency> readPomFile(DependencySet depSet) {
        if (depSet == null || depSet.file == null) {
            return Collections.emptyList();
        }
        if (!depSet.file.exists()) {
            // Build parent chains to show where this transitive dependency was referenced
            // from
            List<String> chains = buildParentChains(depSet);
            if (chains.isEmpty()) {
                getLog().warn("POM file does not exist: " + depSet.file.getAbsolutePath());
            } else {
                for (String chain : chains) {
                    getLog().warn("POM file does not exist: " + depSet.file.getAbsolutePath() + " -- referenced from: "
                            + chain);
                }
            }
            return Collections.emptyList();
        }

        MavenXpp3Reader reader = new MavenXpp3Reader();
        // First try the default Reader (works for most POMs)
        try (FileReader fileReader = new FileReader(depSet.file)) {
            Model model = reader.read(fileReader);
            List<Dependency> dependencies = model.getDependencies();
            return dependencies != null ? dependencies : Collections.emptyList();
        } catch (org.codehaus.plexus.util.xml.pull.XmlPullParserException xppe) {
            // Common issue: file has UTF-8 BOM but XML declares ISO-8859-1 (or similar
            // mismatches).
            // Try a fallback: read raw bytes and parse as UTF-8 (strip BOM if present).
            try {
                byte[] bytes = Files.readAllBytes(depSet.file.toPath());
                // detect and remove UTF-8 BOM if present
                int offset = 0;
                if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                        && (bytes[2] & 0xFF) == 0xBF) {
                    offset = 3;
                }
                String content = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
                try (StringReader sr = new StringReader(content)) {
                    Model model = reader.read(sr);
                    List<Dependency> dependencies = model.getDependencies();
                    return dependencies != null ? dependencies : Collections.emptyList();
                }
            } catch (Exception ex) {
                getLog().error("Fallback parse failed for POM file: " + depSet.file.getAbsolutePath(), ex);
            }
        } catch (Exception e) {
            getLog().error("Error reading POM file: " + depSet.file.getAbsolutePath(), e);
        }
        return Collections.emptyList();
    }

    /**
     * Build all parent chains for a given DependencySet.
     * Each chain is a string like: parentA -> parentB -> dep
     */
    private List<String> buildParentChains(DependencySet depSet) {
        List<String> result = new ArrayList<>();
        if (depSet == null)
            return result;
        if (depSet.preDependencies == null || depSet.preDependencies.isEmpty()) {
            // no parents, return just the coordinate or name
            result.add(depSet.getCoordinate());
            return result;
        }
        // for each parent, build chains recursively
        for (DependencySet parent : depSet.preDependencies) {
            List<String> parentChains = buildParentChains(parent);
            for (String pc : parentChains) {
                result.add(pc + " -> " + depSet.getCoordinate());
            }
        }
        return result;
    }
}