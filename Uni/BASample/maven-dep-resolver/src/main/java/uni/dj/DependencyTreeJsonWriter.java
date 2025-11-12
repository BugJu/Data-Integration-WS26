package uni.dj;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

/**
 * Writes the Aether dependency tree as JSON with the following shape per node:
 * {
 * "groupId": "...",
 * "artifactId": "...",
 * "name": "groupId.artifactId",
 * "version": "...",
 * "scope": "...",
 * "isOmmitted": true|false,
 * "winner": string|null,
 * "isLeaf": true|false,
 * "parent": string|null,
 * "children": [ ... ]
 * }
 */
public class DependencyTreeJsonWriter {

    public void write(DependencyNode root, Writer writer) throws IOException {
        writeNode(root, writer, 0, null);
        writer.flush();
    }

    private void writeNode(DependencyNode node, Writer out, int indent, DependencyNode parent) throws IOException {
        indent(out, indent);
        out.write("{");
        Object winnerObj = node.getData().get(ConflictResolver.NODE_DATA_WINNER);

        // Extract coordinates
        Dependency dep = node.getDependency();
        Artifact art = dep != null ? dep.getArtifact() : null;
        String groupId = art != null ? nullToEmpty(art.getGroupId()) : "";
        String artifactId = art != null ? nullToEmpty(art.getArtifactId()) : "";
        String version = art != null ? nullToEmpty(art.getVersion()) : "";
        String scope = dep != null ? nullToEmpty(dep.getScope()) : "";
        String name = (groupId.isEmpty() && artifactId.isEmpty()) ? "" : (groupId + "." + artifactId);
        String winnerNodeName = winnerObj != null ? winnerObj.toString() : null;

        // Compute omitted/winner and leaf

        boolean isOmmitted = false;
        String winner = null;
        if (dep != null && winnerObj != null) {
            String depStr = dep.toString();
            String winStr = winnerObj.toString();
            isOmmitted = !depStr.equals(winStr);
            if (isOmmitted) {
                winner = winStr;
            }
        }
        boolean isLeaf = node.getChildren() == null || node.getChildren().isEmpty();

        String parentName = null;
        if (parent != null && parent.getDependency() != null && parent.getDependency().getArtifact() != null) {
            Artifact pa = parent.getDependency().getArtifact();
            String pg = nullToEmpty(pa.getGroupId());
            String pi = nullToEmpty(pa.getArtifactId());
            parentName = (pg.isEmpty() && pi.isEmpty()) ? "" : (pg + "." + pi);
        }

        // Fields
        out.write("\n");
        writeStringField(out, "groupId", groupId, indent + 2);
        out.write(",\n");
        writeStringField(out, "artifactId", artifactId, indent + 2);
        out.write(",\n");
        writeStringField(out, "name", name, indent + 2);
        out.write(",\n");
        writeStringField(out, "version", version, indent + 2);
        out.write(",\n");
        writeStringField(out, "scope", scope, indent + 2);
        out.write(",\n");
        writeBooleanField(out, "isOmitted", isOmmitted, indent + 2);
        out.write(",\n");
        writeNullableStringField(out, "winner", winner, indent + 2);
        out.write(",\n");
        writeBooleanField(out, "isLeaf", isLeaf, indent + 2);
        out.write(",\n");
        writeNullableStringField(out, "parent", parentName, indent + 2);
        out.write(",\n");
        writeNullableStringField(out, "winnerNodeName", winnerNodeName, indent + 2);
        out.write(",\n");


        // Children
        indent(out, indent + 2);
        out.write("\"children\": [");
        List<DependencyNode> children = node.getChildren();
        if (children != null && !children.isEmpty()) {
            out.write("\n");
            Iterator<DependencyNode> it = children.iterator();
            while (it.hasNext()) {
                DependencyNode child = it.next();
                writeNode(child, out, indent + 4, node);
                if (it.hasNext()) {
                    out.write(",\n");
                } else {
                    out.write("\n");
                }
            }
            indent(out, indent + 2);
        }
        out.write("]\n");

        indent(out, indent);
        out.write("}");
    }

    private void writeStringField(Writer out, String name, String value, int indent) throws IOException {
        indent(out, indent);
        out.write("\"");
        out.write(escapeJson(name));
        out.write("\": ");
        out.write("\"");
        out.write(escapeJson(value));
        out.write("\"");
    }

    private void writeNullableStringField(Writer out, String name, String value, int indent) throws IOException {
        indent(out, indent);
        out.write("\"");
        out.write(escapeJson(name));
        out.write("\": ");
        if (value == null) {
            out.write("null");
        } else {
            out.write("\"");
            out.write(escapeJson(value));
            out.write("\"");
        }
    }

    private void writeBooleanField(Writer out, String name, boolean value, int indent) throws IOException {
        indent(out, indent);
        out.write("\"");
        out.write(escapeJson(name));
        out.write("\": ");
        out.write(value ? "true" : "false");
    }

    private void indent(Writer out, int spaces) throws IOException {
        for (int i = 0; i < spaces; i++) {
            out.write(' ');
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
