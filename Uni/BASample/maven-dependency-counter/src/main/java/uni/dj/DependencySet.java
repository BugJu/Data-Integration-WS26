package uni.dj;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DependencySet {
    List<DependencySet> preDependencies = new ArrayList<>();
    List<DependencySet> postDependencies = new ArrayList<>();
    String name = "";
    File file = null;
    String groupId = null;
    String artifactId = null;
    String version = null;

    public String getFileName() {
        return file == null ? name : file.toString();
    }

    public String getCoordinate() {
        if (groupId == null || artifactId == null || version == null)
            return name;
        return groupId + ":" + artifactId + ":" + version;
    }

    public String getArtifactId() {
        return artifactId;
    }
}
