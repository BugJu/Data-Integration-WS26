package uni.dj;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Set;

/*
    Mojo that analyzes the dependency tree, finds omitted and unused dependencies,
    and exports the result to JSON and DOT files.
 */
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

    /*
        Main execution point for the Mojo.
        Orchestrates dependency collection, unused dependency analysis,
        and result export.
     */
    @Override
    public void execute() throws MojoExecutionException {
        MavenLogger logger = new MojoMavenLogger(getLog());

        File m2Repo;
        if (settings.getLocalRepository() != null) {
            m2Repo = new File(settings.getLocalRepository());
        } else {
            throw new IllegalStateException("Local repository path is not set in Maven settings.");
        }

        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(repoSession);
        session.setLocalRepositoryManager(repoSession.getLocalRepositoryManager());
        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);

        DependencyService dependencyService = new DependencyService(repoSystem, session);
        ArtifactPathResolver pathResolver = new ArtifactPathResolver(m2Repo, logger);
        UnusedDependencyService unusedDependencyService = new UnusedDependencyService(project, logger);

        try {
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            CollectResult result = dependencyService.collectDependencies(project);
            DependencyNode rootNode = result.getRoot();

            List<Dependency> allDependencies = dependencyService.getAllDependencies(rootNode);
            List<File> filepathList = pathResolver.resolveDependencyFiles(allDependencies);

            logger.info("Found " + allDependencies.size() + " project dependencies");

            Set<org.apache.maven.artifact.Artifact> unusedArtifacts = unusedDependencyService.analyzeUnusedDependencies(filepathList);

            OmittedDependencyFinder finder = new OmittedDependencyFinder(rootNode, unusedArtifacts, logger, pathResolver);

            File outputFile = new File(outputDirectory, "dependency-tree.json");
            try (FileWriter writer = new FileWriter(outputFile)) {
                new DependencyTreeJsonWriter().write(finder.getAllNodes(), writer);
            }
            logger.info("Dependency tree written to " + outputFile.getAbsolutePath());

            new DependencyGraphVisualizer().visualize();

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze dependencies", e);
        }
    }

}