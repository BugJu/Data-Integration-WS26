package uni.dj;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/*
    Writes the dependency tree as a JSON file.
    Includes details about each node and its children.
 */
public class DependencyTreeJsonWriter {

    /*
        Entry point to write the dependency tree list to a Writer.
     */
    public void write(List<DependencyTreeNode> allNodes, Writer writer) throws IOException {
        if (allNodes == null || allNodes.isEmpty()) {
            writer.write("[]");
            writer.flush();
            return;
        }

        // Finde die Root-Nodes (Nodes ohne Parent)
        List<DependencyTreeNode> rootNodes = allNodes.stream()
                .filter(node -> node.getParent() == null)
                .toList();

        for (int i = 0; i < rootNodes.size(); i++) {
            DependencyTreeNode rootNode = rootNodes.get(i);
            writeNode(rootNode, writer, allNodes, 0);
            if (i < rootNodes.size() - 1) {
                writer.write(",\n");
            } else {
                writer.write("\n");
            }
        }
        writer.flush();
    }

    /*
        Recursively writes a single dependency node and its children in JSON format.
     */
    private void writeNode(DependencyTreeNode node, Writer out, List<DependencyTreeNode> allNodes, int indent) throws IOException {
        indent(out, indent);
        out.write("{");

        // Extrahiere groupId und artifactId aus dem Namen (format: "groupId.artifactId")
        String groupId = "";
        String artifactId = "";
        if (node.getName() != null && !node.getName().isEmpty()) {
            String[] parts = node.getName().split("\\.", -1);
            if (parts.length == 2) {
                groupId = parts[0];
                artifactId = parts[1];
            } else if (parts.length > 2) {
                // Falls Name mehrere Punkte hat, nimm letzten Part als artifactId
                groupId = node.getName().substring(0, node.getName().lastIndexOf("."));
                artifactId = parts[parts.length - 1];
            }
        }

        // Fields
        out.write("\n");
        writeStringField(out, "groupId", groupId, indent + 2);
        out.write(",\n");
        writeStringField(out, "artifactId", artifactId, indent + 2);
        out.write(",\n");
        writeStringField(out, "name", node.getName() != null ? node.getName() : "", indent + 2);
        out.write(",\n");
        writeStringField(out, "version", node.getVersion() != null ? node.getVersion() : "", indent + 2);
        out.write(",\n");
        writeStringField(out, "scope", node.getScope() != null ? node.getScope() : "", indent + 2);
        out.write(",\n");
        writeBooleanField(out, "isOmitted", node.isOmmitted != null ? node.isOmmitted : false, indent + 2);
        out.write(",\n");
        writeNullableStringField(out, "winner", node.getWinner() != null && !node.getWinner().isEmpty() ? node.getWinner() : null, indent + 2);
        out.write(",\n");
        writeBooleanField(out, "isLeaf", node.isLeaf != null ? node.isLeaf : false, indent + 2);
        out.write(",\n");
        String parentName = node.getParent() != null ? node.getParent().getName() + ":" + node.getParent().getVersion() +
                " (" + node.getParent().getScope() + ")" : null;
        writeNullableStringField(out, "parent", parentName, indent + 2);
        out.write(",\n");
        writeNullableStringField(out, "winnerNodeName", node.winnerNodeName, indent + 2);
        out.write(",\n");
        writeStringField(out, "pathToDependencyJar", node.pathToDependencyJar != null ? node.pathToDependencyJar : "", indent + 2);
        out.write(",\n");
        writeStringField(out, "pathToDependencyPom", node.getPathToDependencyPom != null ? node.getPathToDependencyPom : "", indent + 2);
        out.write(",\n");
        writeStringField(out, "pathToDependency", node.pathToDependency != null ? node.pathToDependency : "", indent + 2);
        out.write(",\n");
        writeBooleanField(out, "unused", node.unused, indent + 2);
        out.write(",\n");

        // Children
        indent(out, indent + 2);
        out.write("\"children\": [");

        // Finde alle Kinder dieses Nodes
        List<DependencyTreeNode> children = allNodes.stream()
                .filter(n -> n.getParent() != null && n.getParent().equals(node))
                .toList();

        if (!children.isEmpty()) {
            out.write("\n");
            for (int i = 0; i < children.size(); i++) {
                DependencyTreeNode child = children.get(i);
                writeNode(child, out, allNodes, indent + 4);
                if (i < children.size() - 1) {
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

    /*
        Writes a JSON string field with proper indentation and escaping.
     */
    private void writeStringField(Writer out, String name, String value, int indent) throws IOException {
        indent(out, indent);
        out.write("\"");
        out.write(escapeJson(name));
        out.write("\": ");
        out.write("\"");
        out.write(escapeJson(value != null ? value : ""));
        out.write("\"");
    }

    /*
        Writes a JSON field that can be a string or null.
     */
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

    /*
        Writes a JSON boolean field.
     */
    private void writeBooleanField(Writer out, String name, boolean value, int indent) throws IOException {
        indent(out, indent);
        out.write("\"");
        out.write(escapeJson(name));
        out.write("\": ");
        out.write(value ? "true" : "false");
    }

    /*
        Helper method to write spaces for JSON indentation.
     */
    private void indent(Writer out, int spaces) throws IOException {
        for (int i = 0; i < spaces; i++) {
            out.write(' ');
        }
    }

    /*
        Escapes special characters in a string for JSON compliance.
        @returns Escaped string.
     */
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
}