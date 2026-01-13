package uni.dj;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UnusedDependencyService {

    private final MavenProject project;
    private final MavenLogger logger;

    public UnusedDependencyService(MavenProject project, MavenLogger logger) {
        this.project = project;
        this.logger = logger;
    }

    public Set<Artifact> analyzeUnusedDependencies(List<File> dependencyJars) throws Exception {
        File classesDir = new File(project.getBuild().getOutputDirectory());
        UnusedDependencyAnalyzer analyzer = new UnusedDependencyAnalyzer(logger);

        logger.info("=== Analyzing Project Bytecode ===");
        analyzer.analyzeProjectUsage(classesDir);

        logger.info("=== Checking All Dependencies (Direct + Transitive) ===");
        Set<Artifact> unusedArtifacts = new HashSet<>();

        int usedCount = 0;
        int unusedCount = 0;

        for (File jarFile : dependencyJars) {
            if (jarFile.exists() && jarFile.getName().endsWith(".jar")) {
                boolean isUsed = analyzer.isDependencyUsed(jarFile);
                String artifactInfo = extractArtifactInfo(jarFile);

                if (!isUsed) {
                    logger.warn("  UNUSED: " + artifactInfo);
                    Artifact mvnArtifact = createMavenArtifact(jarFile);
                    if (mvnArtifact != null) {
                        unusedArtifacts.add(mvnArtifact);
                    }
                    unusedCount++;
                } else {
                    logger.debug("  USED: " + artifactInfo);
                    usedCount++;
                }
            }
        }

        logger.info("=== Dependency Analysis Complete ===");
        logger.info("Total Dependencies Analyzed: " + dependencyJars.size());
        logger.info("Used Dependencies: " + usedCount);
        logger.info("Unused Dependencies: " + unusedCount);

        if (unusedCount > 0) {
            logger.warn("⚠️ Found " + unusedCount + " potentially unused dependencies!");
        } else {
            logger.info("✅ All dependencies are being used!");
        }

        return unusedArtifacts;
    }

    private String extractArtifactInfo(File jarFile) {
        String path = jarFile.getAbsolutePath();
        String[] parts = path.split("[\\\\/]");

        if (parts.length >= 3) {
            String version = parts[parts.length - 2];
            String artifactId = parts[parts.length - 3];

            StringBuilder groupId = new StringBuilder();
            for (int i = parts.length - 4; i >= 0; i--) {
                if (parts[i].equals("repository")) break;
                if (groupId.length() > 0) groupId.insert(0, ".");
                groupId.insert(0, parts[i]);
            }
            return groupId + ":" + artifactId + ":" + version;
        }
        return jarFile.getName();
    }

    private Artifact createMavenArtifact(File jarFile) {
        String path = jarFile.getAbsolutePath();
        String[] parts = path.split("[\\\\/]");

        if (parts.length >= 3) {
            String version = parts[parts.length - 2];
            String artifactId = parts[parts.length - 3];

            StringBuilder groupId = new StringBuilder();
            for (int i = parts.length - 4; i >= 0; i--) {
                if (parts[i].equals("repository")) break;
                if (groupId.length() > 0) groupId.insert(0, ".");
                groupId.insert(0, parts[i]);
            }

            Artifact artifact = new DefaultArtifact(
                    groupId.toString(),
                    artifactId,
                    version,
                    "compile",
                    "jar",
                    null,
                    new DefaultArtifactHandler("jar")
            );
            artifact.setFile(jarFile);
            return artifact;
        }
        return null;
    }
}
