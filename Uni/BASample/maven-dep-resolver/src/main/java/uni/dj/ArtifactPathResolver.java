package uni.dj;

import org.eclipse.aether.graph.Dependency;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ArtifactPathResolver {

    private final File localRepository;
    private final MavenLogger logger;

    public ArtifactPathResolver(File localRepository, MavenLogger logger) {
        this.localRepository = localRepository;
        this.logger = logger;
    }

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

    public File resolveDependencyFile(Dependency dep) {
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

        String classifier = dep.getArtifact().getClassifier();
        String jarName = dep.getArtifact().getArtifactId() + "-"
                + dep.getArtifact().getVersion();
        if (classifier != null && !classifier.isEmpty()) {
            jarName += "-" + classifier;
        }
        jarName += ".jar";

        pathBuilder.append(jarName);
        File depFile = new File(localRepository, pathBuilder.toString());

        if (depFile.exists() && depFile.isFile() && depFile.canRead()) {
            logger.info("Found JAR: " + depFile.getAbsolutePath());
            return depFile;
        } else {
            logger.warn("JAR NOT FOUND or NOT READABLE: " + depFile.getAbsolutePath());
            return null;
        }
    }

    public String[] buildDependencyFilePath(org.eclipse.aether.graph.DependencyNode node) {
        String[] retArray = new String[3];
        String groupId = node.getArtifact().getGroupId();
        String[] parts = groupId.split("\\.");
        StringBuilder pathBuilder = new StringBuilder();

        for (String part : parts) {
            pathBuilder.append(File.separator).append(part);
        }

        pathBuilder.append(File.separator)
                .append(node.getArtifact().getArtifactId())
                .append(File.separator)
                .append(node.getArtifact().getVersion())
                .append(File.separator);

        File pathToDependency = new File(localRepository, pathBuilder.toString());
        retArray[0] = pathToDependency.toString();

        String classifier = node.getArtifact().getClassifier();
        String jarName = node.getArtifact().getArtifactId() + "-"
                + node.getArtifact().getVersion();
        if (classifier != null && !classifier.isEmpty()) {
            jarName += "-" + classifier;
        }
        jarName += ".jar";

        String jarPathStr = pathBuilder.toString() + jarName;
        File depFile = new File(localRepository, jarPathStr);
        retArray[1] = depFile.toString();
        retArray[2] = depFile.toString().replace(".jar", ".pom");
        return retArray;
    }
}
