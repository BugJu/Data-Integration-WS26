package uni.dj.mavendepplugin

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

var allNodes = listOf<Dependency>()
var dependencyTree: Dependency? = null

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val basePath = project.basePath ?: return
        val dependencyTreeFile = File(basePath, "target/dependency-tree.json")

        // Hauptpanel
        val mainPanel = JPanel(BorderLayout())

        // Reload-Button
        val reloadButton = JButton("Reload")

        // Suchbereich
        val searchPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val searchField = JTextField(20)
        searchField.preferredSize = Dimension(200, 30)
        searchPanel.add(JLabel("Suche:"))
        searchPanel.add(searchField)

        // JTree für Anzeige
        val treePanel = JPanel(BorderLayout())
        var currentTree: JTree? = null

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                updateTreeWithSearch(searchField.text, treePanel, currentTree) { newTree ->
                    currentTree = newTree
                }
            }

            override fun removeUpdate(e: DocumentEvent?) {
                updateTreeWithSearch(searchField.text, treePanel, currentTree) { newTree ->
                    currentTree = newTree
                }
            }

            override fun changedUpdate(e: DocumentEvent?) {}
        })

        // Funktion zum UI updaten
        fun updateUI() {
            mainPanel.removeAll()

            if (dependencyTreeFile.exists()) {
                mainPanel.add(searchPanel, BorderLayout.NORTH)
                mainPanel.add(treePanel, BorderLayout.CENTER)
                parseJson(project)

                // Zeige initial den ganzen Baum
                val rootNode = createTreeRoot(dependencyTree!!)
                val tree = JTree(rootNode)
                tree.isRootVisible = true
                tree.expandRow(0)
                currentTree = tree

                treePanel.removeAll()
                treePanel.add(JScrollPane(tree), BorderLayout.CENTER)
                treePanel.revalidate()
                treePanel.repaint()
            } else {
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

private fun createTreeRoot(dependency: Dependency): DefaultMutableTreeNode {
    val label = "${dependency.name} (${dependency.version}) (${dependency.scope})"
    val rootNode = DefaultMutableTreeNode(label)
    dependency.children.forEach { child ->
        rootNode.add(createTreeRoot(child))
    }
    return rootNode
}

private fun updateTreeWithSearch(
    searchTerm: String,
    treePanel: JPanel,
    currentTree: JTree?,
    updateTree: (JTree) -> Unit
) {
    if (searchTerm.isBlank()) {
        // Zeige ganzen Baum wenn Suchfeld leer
        if (dependencyTree != null) {
            val rootNode = createTreeRoot(dependencyTree!!)
            val tree = JTree(rootNode)
            tree.isRootVisible = true
            tree.expandRow(0)
            updateTree(tree)

            treePanel.removeAll()
            treePanel.add(JScrollPane(tree), BorderLayout.CENTER)
            treePanel.revalidate()
            treePanel.repaint()
        }
        return
    }

    // Finde alle Knoten, die den Suchbegriff enthalten
    val matchingNodes = allNodes.filter {
        it.artifactId.contains(searchTerm, ignoreCase = true) ||
                it.groupId.contains(searchTerm, ignoreCase = true)
    }

    if (matchingNodes.isEmpty()) {
        treePanel.removeAll()
        treePanel.add(JLabel("Keine Ergebnisse"), BorderLayout.CENTER)
        treePanel.revalidate()
        treePanel.repaint()
        return
    }

    // Baue einen Baum nur aus gefundenen Knoten
    val rootNode = DefaultMutableTreeNode("Suchergebnisse")
    matchingNodes.forEach { matchedDep ->
        val label = "${matchedDep.artifactId} (${matchedDep.version}) - ${matchedDep.scope}"
        val node = DefaultMutableTreeNode(label)

        // Zeige auch die Kinder der gefundenen Knoten
        matchedDep.children.forEach { child ->
            node.add(createTreeRoot(child))
        }

        rootNode.add(node)
    }

    val tree = JTree(rootNode)
    tree.isRootVisible = true
    tree.expandRow(0) // Wurzel expandieren
    updateTree(tree)

    treePanel.removeAll()
    treePanel.add(JScrollPane(tree), BorderLayout.CENTER)
    treePanel.revalidate()
    treePanel.repaint()
}

private fun parseJson(project: Project) {
    val basePath = project.basePath ?: return
    val jsonDB = File(basePath, "target/dependency-tree.json")
    if (!jsonDB.exists()) return

    val jsonText = jsonDB.readText()
    val gson = Gson()
    dependencyTree = gson.fromJson(jsonText, Dependency::class.java)
    allNodes = collectAllNodes(dependencyTree!!)
    println("Alle Knoten: ${allNodes.size}")
}

fun collectAllNodes(node: Dependency, list: MutableList<Dependency> = mutableListOf()): List<Dependency> {
    list.add(node)
    node.children.forEach { child ->
        collectAllNodes(child, list)
    }
    return list
}
