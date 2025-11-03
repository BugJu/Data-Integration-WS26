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
import org.opalj.br.analyses.cg.ApplicationEntryPointsFinder;
import scala.collection.mutable.ListBuffer;
import scala.jdk.javaapi.CollectionConverters;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.io.File;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;

@Mojo(name = "analyze-dependencies", defaultPhase = LifecyclePhase.COMPILE)
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
        MavenLogger logger = new MavenLogger() {
            @Override
            public void info(String msg) {
                getLog().info(msg);
            }

            @Override
            public void debug(String msg) {
                getLog().debug(msg);
            }

            @Override
            public void warn(String msg) {
                getLog().warn(msg);
            }

            @Override
            public void error(String msg) {
                getLog().error(msg);
            }
        };

        File m2Repo;
        if (settings.getLocalRepository() != null) {
            m2Repo = new File(settings.getLocalRepository());
        } else {
            throw new IllegalStateException("Local repository path is not set in Maven settings.");
        }

        List<File> javaList;
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

            // Write dependency tree
            File outputFile = new File(outputDirectory, "dependency-tree.txt");
            try (FileWriter writer = new FileWriter(outputFile)) {
                result.getRoot().accept(new DependencyTreePrinter(writer));
            }
            logger.info("Dependency tree written to " + outputFile.getAbsolutePath());

            // Get all dependencies (Project + Plugin)
            List<Dependency> projectDependencies = collectDependenciesAsList(result.getRoot());
            // TODO: Maybe enable a mode where both or only plugin dependencies are evaluated
            //List<Dependency> pluginDependencies = buildPluginDependencyTree();

            // Kombiniere beide Listen
            List<Dependency> allDependencies = new ArrayList<>(projectDependencies);
            // TODO: Maybe enable a mode where both or only plugin dependencies are evaluated
            //allDependencies.addAll(pluginDependencies);

            // Entferne Duplikate basierend auf Koordinaten
            Map<String, Dependency> uniqueDeps = new HashMap<>();
            for (Dependency dep : allDependencies) {
                String key = dep.getArtifact().getGroupId() + ":" +
                        dep.getArtifact().getArtifactId() + ":" +
                        dep.getArtifact().getVersion();
                uniqueDeps.putIfAbsent(key, dep);
            }

            List<Dependency> dependencies = new ArrayList<>(uniqueDeps.values());
            javaList = buildDependencyFilePath(dependencies, m2Repo);

            logger.info("Found " + projectDependencies.size() + " project dependencies");
            // TODO: Maybe enable a mode where both or only plugin dependencies are evaluated
            //logger.info("Found " + pluginDependencies.size() + " plugin dependencies");
            logger.info("Total unique dependencies: " + dependencies.size());

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to collect dependencies", e);
        }

        // ✅ KRITISCH: Erstelle JAR → Klasse Mapping
        ListBuffer<OpalFindUnreachableDependencies.JarClassMapping> jarClassMappings =
                new ListBuffer<>();

        for (File jarFile : javaList) {
            String jarName = jarFile.getName();
            try {
                Set<String> classesInJar = extractClassNamesFromJar(jarFile);

                if (classesInJar.isEmpty()) {
                    logger.debug("JAR " + jarName + " contains no classes (skipping)");
                    continue;
                }

                logger.debug("JAR " + jarName + " contains " + classesInJar.size() + " classes");

                // Konvertiere Java Set zu Scala immutable Set
                scala.collection.immutable.Set<String> scalaSet =
                        scala.collection.JavaConverters.asScalaSet(classesInJar).toSet();

                OpalFindUnreachableDependencies.JarClassMapping mapping =
                        new OpalFindUnreachableDependencies.JarClassMapping(jarName, scalaSet);
                jarClassMappings.addOne(mapping);
            } catch (IOException e) {
                logger.warn("Could not extract classes from JAR: " + jarName + " (" + e.getMessage() + ")");
            }
        }

        logger.info("Created " + jarClassMappings.size() + " JAR class mappings");

        // Convert to Scala collections
        ListBuffer<File> depsWithFilePaths = new ListBuffer<>();
        depsWithFilePaths.addAll(CollectionConverters.asScala(javaList));

        logger.info("Starting OPAL analysis with " + jarClassMappings.size() + " JAR mappings...");

        //✅ Call Scala analysis with JAR mappings
        OpalFindUnreachableDependencies.findUnreachableDependencies(
                new File(project.getBuild().getOutputDirectory()).getAbsolutePath(),  // ← target/classes statt Projekt-Root
                logger,
                depsWithFilePaths,
                jarClassMappings,
                outputDirectory,
                "callgraph.dot"
        );
        logger.info("found " + depsWithFilePaths.size() + "dependencies with files" + "\n" + depsWithFilePaths.take(10).toString());
    }

    private Set<String> extractClassNamesFromJar(File jarFile) throws IOException {
        Set<String> classNames = new HashSet<>();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".class") && !name.startsWith("META-INF")) {
                    // Konvertiere: "com/example/MyClass.class" → "com/example/MyClass"
                    // WICHTIG: Behalte Slashes (/) bei, da OPAL FQNs mit Slashes verwendet!
                    String className = name.replaceAll("\\.class$", "");
                    classNames.add(className);
                }
            }
        }

        return classNames;
    }

    public List<Dependency> buildPluginDependencyTree() {
        List<Dependency> allPluginDeps = new ArrayList<>();  // ← Sammle ALLE
        File pluginDepFile = new File(outputDirectory, "plugin-dependency-tree.txt");
        try (FileWriter pluginWriter = new FileWriter(pluginDepFile)) {
            for (Plugin plugin : project.getBuild().getPlugins()) {
                String groupId = plugin.getGroupId();
                String artifactId = plugin.getArtifactId();
                String version = plugin.getVersion();

                if (groupId == null || version == null) {
                    getLog().warn("Skipping plugin (missing coordinates): " + plugin);
                    continue;
                }

                String coords = groupId + ":" + artifactId + ":" + version;
                DefaultArtifact pluginArtifact = new DefaultArtifact(coords);

                CollectRequest pluginReq = new CollectRequest();
                pluginReq.setRoot(new Dependency(pluginArtifact, ""));
                pluginReq.setRepositories(project.getRemotePluginRepositories());

                CollectResult pluginResult = repoSystem.collectDependencies(repoSession, pluginReq);

                // ✅ FÜR JEDEN PLUGIN einzeln sammeln!
                List<Dependency> pluginDeps = collectDependenciesAsList(pluginResult.getRoot());
                allPluginDeps.addAll(pluginDeps);

                getLog().info("Found " + pluginDeps.size() + " dependencies for plugin: " + coords);
                pluginWriter.write("Dependency tree for plugin: " + coords + "\n");
                pluginResult.getRoot().accept(new DependencyTreePrinter(pluginWriter));
                pluginWriter.write("\n");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to collect plugin dependencies", e);
        }

        return allPluginDeps;  // ← Gibt ALLE Plugins zurück!
    }

    public List<Dependency> collectDependenciesAsList(DependencyNode node) {
        List<Dependency> deps = new ArrayList<>();
        if (node != null) {
            collectRecursively(node, deps);
        }
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

            pathBuilder.append(File.separator)
                    .append(dep.getArtifact().getArtifactId())
                    .append(File.separator)
                    .append(dep.getArtifact().getVersion())
                    .append(File.separator);

            // Beachte Classifier!
            String classifier = dep.getArtifact().getClassifier();
            String jarName = dep.getArtifact().getArtifactId() + "-"
                    + dep.getArtifact().getVersion();
            if (classifier != null && !classifier.isEmpty()) {
                jarName += "-" + classifier;
            }
            jarName += ".jar";

            pathBuilder.append(jarName);
            File depFile = new File(m2Repo, pathBuilder.toString());

            // ✅ KRITISCH: Nur existierende Dateien hinzufügen
            if (depFile.exists() && depFile.isFile() && depFile.canRead()) {
                filePaths.add(depFile);
                getLog().info("Found JAR: " + depFile.getAbsolutePath());
            } else {
                getLog().warn("JAR NOT FOUND or NOT READABLE: " + depFile.getAbsolutePath());
            }
        }

        return filePaths;
    }

}