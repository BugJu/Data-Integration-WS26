package uni.dj;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
    Service for analyzing which dependencies are actually used by the project's bytecode
 */
public record UnusedDependencyService(MavenProject project, MavenLogger logger) {

    /*
        Analyzes a list of dependency JARs to determine if they are used by the project
        @returns Set of unused artifacts
     */
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

    /*
        Extracts GAV (GroupId, ArtifactId, Version) info from a JAR file path
        @returns String in the format groupid:artifactid:version
     */
    private String extractArtifactInfo(File jarFile) {
        String path = jarFile.getAbsolutePath();
        String[] parts = path.split("[\\\\/]");

        if (parts.length >= 3) {
            String version = parts[parts.length - 2];
            String artifactId = parts[parts.length - 3];

            StringBuilder groupId = new StringBuilder();
            for (int i = parts.length - 4; i >= 0; i--) {
                if (parts[i].equals("repository")) break;
                if (!groupId.isEmpty()) groupId.insert(0, ".");
                groupId.insert(0, parts[i]);
            }
            return groupId + ":" + artifactId + ":" + version;
        }
        return jarFile.getName();
    }

    /*
        Creates a Maven Artifact object from a JAR file by parsing its path
        @returns Maven Artifact, or null if parsing fails
     */
    private Artifact createMavenArtifact(File jarFile) {
        String path = jarFile.getAbsolutePath();
        String[] parts = path.split("[\\\\/]");

        if (parts.length >= 3) {
            String version = parts[parts.length - 2];
            String artifactId = parts[parts.length - 3];

            StringBuilder groupId = new StringBuilder();
            for (int i = parts.length - 4; i >= 0; i--) {
                if (parts[i].equals("repository")) break;
                if (!groupId.isEmpty()) groupId.insert(0, ".");
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
