package uni.dj;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import java.io.Writer;
import java.io.IOException;

public class DependencyTreePrinter implements DependencyVisitor {

    private final Writer writer;
    private int depth = 0;

    public DependencyTreePrinter(Writer writer) {
        this.writer = writer;
    }

    private void indent() throws IOException {
        for (int i = 0; i < depth; i++) {
            writer.write("  ");
        }
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
        try {
            indent();
            if (node.getDependency() != null && node.getData().get(ConflictResolver.NODE_DATA_WINNER) != null &&
                    !(node.getDependency().toString().equals(node.getData().get(ConflictResolver.NODE_DATA_WINNER).toString()))) {
                writer.write(node.getDependency().toString() + "(-- wurde Ommited aufgrund von:" + node.getData().get(ConflictResolver.NODE_DATA_WINNER).toString() + ")");
            } else {
                writer.write(node.getDependency().toString());
            }
            writer.write("\n");
            depth++;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
        depth--;
        return true;
    }
}
