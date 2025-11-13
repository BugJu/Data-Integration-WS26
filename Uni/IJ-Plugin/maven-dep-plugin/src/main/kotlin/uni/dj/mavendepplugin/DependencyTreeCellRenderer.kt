package uni.dj.mavendepplugin

import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

class DependencyTreeCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
        val node = value as? DefaultMutableTreeNode
        val userObject = node?.userObject

        val displayText = when (userObject) {
            is Dependency -> buildLabel(userObject)
            is String -> userObject
            else -> userObject.toString()
        }

        super.getTreeCellRendererComponent(tree, displayText, selected, expanded, leaf, row, hasFocus)
        return this
    }
}