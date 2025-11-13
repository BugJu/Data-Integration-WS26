package uni.dj.mavendepplugin

import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class DependencyListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

        if (value is Dependency) {
            val displayText = buildLabel(value)
            text = displayText
        }

        return component
    }
}