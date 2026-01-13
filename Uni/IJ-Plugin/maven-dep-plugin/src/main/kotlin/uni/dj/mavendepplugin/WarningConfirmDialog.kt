package uni.dj.mavendepplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import com.intellij.icons.AllIcons
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

class WarningConfirmDialog(
    project: Project, private val message: String, private var title: String = "Warnung"
) : DialogWrapper(project) {
    init {
        init()
        this.title = title
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(16, 12)
        }

        // Icon + Message Panel
        val contentPanel = JPanel(BorderLayout(8, 0)).apply {
            border = JBUI.Borders.empty()
        }

        // Warning Icon (IntelliJ Standard)
        val iconLabel = JLabel(AllIcons.General.WarningDialog)
        contentPanel.add(iconLabel, BorderLayout.WEST)

        // Text Area für die Nachricht (mit automatischem Zeilenumbruch)
        val textArea = JTextArea(message).apply {
            isEditable = false
            isFocusable = false
            lineWrap = true
            wrapStyleWord = true
            background = null
            font = UIManager.getFont("Label.font")
            margin = JBUI.insets(0, 5)
        }

        // Maximale Breite festlegen, damit ScrollPane sinnvoll arbeitet
        val scrollPane = JScrollPane(textArea).apply {
            border = null
            preferredSize = Dimension(350, 80)
        }

        contentPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(contentPanel, BorderLayout.CENTER)

        return mainPanel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction, cancelAction)
    }
}
