package uni.dj.mavendepplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.*
import java.io.File
import javax.swing.*

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val basePath = project.basePath ?: return
        val dependencyTreeFile = File(basePath, "target/dependency-tree.json")

        // Hauptpanel
        val mainPanel = JPanel(BorderLayout())

        // Reload-Button (initial)
        val reloadButton = JButton("Reload")

        // Suchbereich (wird später angezeigt)
        val searchPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val searchField = JTextField(20)
        searchField.preferredSize = Dimension(200, 30)
        val searchButton = JButton("Suchen")
        searchPanel.add(searchField)
        searchPanel.add(searchButton)

        // Inhalt

        // Funktion zum UI updaten
        fun updateUI() {
            mainPanel.removeAll()

            if (dependencyTreeFile.exists()) {
                // Datei existiert -> Suchleiste anzeigen
                mainPanel.add(searchPanel, BorderLayout.NORTH)
            } else {
                // Datei existiert nicht -> Nur Reload-Button
                val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER))
                buttonPanel.add(reloadButton)
                mainPanel.add(buttonPanel, BorderLayout.CENTER)
            }

            mainPanel.revalidate()
            mainPanel.repaint()
        }

        // Reload-Button Listener
        reloadButton.addActionListener {
            updateUI()
        }

        // Initial UI laden
        updateUI()

        // Ins ToolWindow
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
