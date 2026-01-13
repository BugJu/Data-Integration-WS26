package uni.dj;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/*
    resolves maven aether dependencies to file paths in local repositery (.m2)
 */
public record ArtifactPathResolver(File localRepository, MavenLogger logger) {

    /*
    @returns List<File> filepath, list of filePaths of all dependencies
     */
    public List<File> resolveDependencyFiles(List<Dependency> dependencies) {
        List<File> filePaths = new ArrayList<>();

        for (Dependency dep : dependencies) {
            File depFile = resolveDependencyFile(dep);
            if (depFile != null) {
                filePaths.add(depFile);
            }
        }
        return filePaths;
    }

    /*
        builds depdency File paths and checks if Filepaths actually exist in Local Repository
        @returns null, if filepath does not exist
        @returns File depFile, if the filepath exists
     */
    public File resolveDependencyFile(Dependency dep) {
        String[] paths = buildDependencyFilePath(dep.getArtifact());
        File depFile = new File(paths[1]);

        if (depFile.exists() && depFile.isFile() && depFile.canRead()) {
            logger.info("Found JAR: " + depFile.getAbsolutePath());
            return depFile;
        } else {
            logger.warn("JAR NOT FOUND or NOT READABLE: " + depFile.getAbsolutePath());
            return null;
        }
    }

    /*
    Build filePath from artifact groupID, artifactID, version (gav)
    @returns array with .pom/.jar filepaths
     */
    String[] buildDependencyFilePath(Artifact artifact) {
        String[] retArray = new String[3];
        String groupId = artifact.getGroupId();
        String[] parts = groupId.split("\\.");
        StringBuilder pathBuilder = new StringBuilder();

        for (String part : parts) {
            pathBuilder.append(File.separator).append(part);
        }

        pathBuilder.append(File.separator)
                .append(artifact.getArtifactId())
                .append(File.separator)
                .append(artifact.getVersion())
                .append(File.separator);

        File pathToDependency = new File(localRepository, pathBuilder.toString());
        retArray[0] = pathToDependency.getAbsolutePath();

        String classifier = artifact.getClassifier();
        String jarName = artifact.getArtifactId() + "-"
                + artifact.getVersion();
        if (classifier != null && !classifier.isEmpty()) {
            jarName += "-" + classifier;
        }
        jarName += ".jar";

        File depFile = new File(pathToDependency, jarName);
        retArray[1] = depFile.getAbsolutePath();
        retArray[2] = depFile.getAbsolutePath().replace(".jar", ".pom");
        return retArray;
    }
}
