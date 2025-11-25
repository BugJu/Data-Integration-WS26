package uni.dj;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;


import scala.collection.mutable.ListBuffer;
import scala.jdk.javaapi.CollectionConverters;

import org.apache.maven.execution.MavenSession;


import java.io.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;

@Mojo(name = "analyze-dependencies", defaultPhase = LifecyclePhase.PACKAGE)
public class GetDependencyTreeMojo extends AbstractMojo {

    @Parameter(defaultValue = "${settings}", required = true, readonly = true)
    Settings settings;
    @Component
    private RepositorySystem repoSystem;


    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component
    private BuildPluginManager pluginManager;

    @Component
    private ProjectDependencyAnalyzer dependencyAnalyzer;

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
        List<Dependency> directDependencyList = new ArrayList<>();
        List<Dependency> transitiveDependencies;

        File m2Repo;
        if (settings.getLocalRepository() != null) {
            m2Repo = new File(settings.getLocalRepository());
        } else {
            throw new IllegalStateException("Local repository path is not set in Maven settings.");
        }
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(repoSession);
        session.setLocalRepositoryManager(repoSession.getLocalRepositoryManager());
        session.setConfigProperty(
                ConflictResolver.CONFIG_PROP_VERBOSE,
                true
        );
        List<File> filepathList;

        try {
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            //Start DepClean Plugin to find unused Dependencies
            getLog().info("Starting dependency analysis...");
            //File depcleanJsonFile = new File(outputDirectory, "depclean-report.json");

            //grab ununsed Dependencies via Maven Dependency plugin
            Set<org.apache.maven.artifact.Artifact> unusedArtifacts = executeDepCleanCore();


            String coords = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
            DefaultArtifact artifact = new DefaultArtifact(coords);

            CollectRequest request = new CollectRequest();
            request.setRoot(new Dependency(artifact, ""));
            request.setRepositories(project.getRemoteProjectRepositories());

            CollectResult result = repoSystem.collectDependencies(session, request);

            // Get all dependencies (Project + Plugin)
            List<Dependency> projectDependencies = collectDependenciesAsList(result.getRoot());
            // TODO: Maybe enable a mode where both or only plugin dependencies are evaluated
            //List<Dependency> pluginDependencies = buildPluginDependencyTree();
            List<DependencyNode> directDependencies = result.getRoot().getChildren();
            for (DependencyNode node : directDependencies) {
                directDependencyList.add(node.getDependency());
            }
            transitiveDependencies = collectTransitiveDependencies(result.getRoot());

            List<Dependency> allDependencies = new ArrayList<>(projectDependencies);
            // TODO: Maybe enable a mode where both or only plugin dependencies are evaluated

            List<Dependency> dependencies = new ArrayList<>(allDependencies);
            filepathList = buildDependencyFilePath(dependencies, m2Repo);

            logger.info("Found " + projectDependencies.size() + " project dependencies");
            // TODO: Maybe enable a mode where both or only plugin dependencies are evaluated

            OmittedDependencyFinder finder = new OmittedDependencyFinder(result.getRoot(), unusedArtifacts, logger, m2Repo);

            File outputFile = new File(outputDirectory, "dependency-tree.json");
            try (FileWriter writer = new FileWriter(outputFile)) {
                new DependencyTreeJsonWriter().write(finder.getAllNodes(), writer);
            }
            logger.info("Dependency tree written to " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to collect dependencies", e);
        }


        // Convert to Scala collections
        ListBuffer<File> depsWithFilePaths = new ListBuffer<>();
        depsWithFilePaths.addAll(CollectionConverters.asScala(filepathList));
        ListBuffer<Dependency> directDependencyListBuf = new ListBuffer<>();
        directDependencyListBuf.addAll(CollectionConverters.asScala(directDependencyList));
        ListBuffer<Dependency> transitiveDependenciesBuf = new ListBuffer<>();
        transitiveDependenciesBuf.addAll(CollectionConverters.asScala(transitiveDependencies));
    }

    private Set<org.apache.maven.artifact.Artifact> executeDepCleanCore() throws MojoExecutionException {
        try {
            ProjectDependencyAnalysis result = dependencyAnalyzer.analyze(project);

            Set<org.apache.maven.artifact.Artifact> unusedDeclaredArtifacts =
                    result.getUnusedDeclaredArtifacts();
            Set<org.apache.maven.artifact.Artifact> usedDeclaredArtifacts =
                    result.getUsedDeclaredArtifacts();
            Set<org.apache.maven.artifact.Artifact> usedUndeclaredArtifacts =
                    result.getUsedUndeclaredArtifacts();

            getLog().info("=== Maven Dependency Analysis Results ===");

            getLog().info("--- Unused Declared Dependencies ---");
            if (unusedDeclaredArtifacts.isEmpty()) {
                getLog().info("  (none)");
            } else {
                unusedDeclaredArtifacts.forEach(artifact ->
                        getLog().warn("  - " + artifact.getGroupId() + ":"
                                + artifact.getArtifactId() + ":" + artifact.getVersion())
                );
            }

            getLog().info("--- Used Declared Dependencies ---");
            usedDeclaredArtifacts.forEach(artifact ->
                    getLog().info("  - " + artifact.getGroupId() + ":"
                            + artifact.getArtifactId() + ":" + artifact.getVersion())
            );

            getLog().info("--- Used but Undeclared Dependencies ---");
            usedUndeclaredArtifacts.forEach(artifact ->
                    getLog().info("  - " + artifact.getGroupId() + ":"
                            + artifact.getArtifactId() + ":" + artifact.getVersion())
            );

            return unusedDeclaredArtifacts;

        } catch (Exception e) {
            throw new MojoExecutionException("Dependency analysis failed", e);
        }
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
        File pluginDepFile = new File(outputDirectory, "plugin-dependency-tree.json");
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
                pluginReq.setRoot(new Dependency(pluginArtifact, JavaScopes.COMPILE));
                pluginReq.setRepositories(project.getRemotePluginRepositories());

                CollectResult pluginResult = repoSystem.collectDependencies(repoSession, pluginReq);


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
            collectRecursively(node, deps, false);
        }
        return deps;
    }


    private List<Dependency> collectTransitiveDependencies(DependencyNode root) {
        List<Dependency> transitiveDeps = new ArrayList<>();

        // Iteriere über die direkten Dependencies (root.getChildren())
        for (DependencyNode directChild : root.getChildren()) {
            // Für jedes direkte Kind: sammle nur DESSEN Kinder rekursiv
            collectRecursively(directChild, transitiveDeps, true); // skipFirst = true
        }

        return transitiveDeps;
    }

    private void collectRecursively(DependencyNode node, List<Dependency> depList, boolean skipFirst) {
        // Überspringe den Knoten selbst beim ersten Aufruf (= die direkte Dependency)
        if (!skipFirst && node.getDependency() != null) {
            depList.add(node.getDependency());
        }

        // Sammle alle Kinder rekursiv (ohne sie zu überspringen)
        for (DependencyNode child : node.getChildren()) {
            collectRecursively(child, depList, false);
        }
    }


    List<File> buildDependencyFilePath(List<Dependency> dependencies, File m2Repo) {
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
