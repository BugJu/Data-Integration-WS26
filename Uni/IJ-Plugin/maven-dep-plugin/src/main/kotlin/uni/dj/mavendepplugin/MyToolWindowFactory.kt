package uni.dj.mavendepplugin

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.Collections
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

var allNodes = listOf<Dependency>()
var dependencyTree: Dependency? = null

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val basePath = project.basePath ?: return
        val dependencyTreeFile = File(basePath, "target/dependency-tree.json")

        val mainPanel = JPanel(BorderLayout())

        // Refresh Button
        val refreshButton = JButton("Refresh JSON")
        val omittedFilterButton = JCheckBox("Search in Omitteds")
        var isOmittedFilterActive = false

        val searchPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val searchField = JTextField(20)
        searchField.preferredSize = Dimension(200, 30)
        searchPanel.add(JLabel("Suche:"))
        searchPanel.add(searchField)
        searchPanel.add(refreshButton)
        searchPanel.add(omittedFilterButton)

        val treePanel = JPanel(BorderLayout())
        var currentTree: JTree? = null

        // Definiere updateUI() HIER, BEVOR es verwendet wird
        fun updateUI() {
            mainPanel.removeAll()

            if (dependencyTreeFile.exists()) {
                mainPanel.add(searchPanel, BorderLayout.NORTH)
                mainPanel.add(treePanel, BorderLayout.CENTER)
                parseJson(project)

                val rootNode = createTreeRoot(dependencyTree!!)
                val tree = JTree(rootNode)
                tree.isRootVisible = true
                tree.expandRow(0)
                tree.cellRenderer = DependencyTreeCellRenderer()
                currentTree = tree
                setupDoubleClickListener(tree, project)

                treePanel.removeAll()
                treePanel.add(JScrollPane(tree), BorderLayout.CENTER)
                treePanel.revalidate()
                treePanel.repaint()
            } else {
                val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER))
                val reloadButton = JButton("Reload")
                reloadButton.addActionListener {
                    updateUI()
                }
                buttonPanel.add(reloadButton)
                mainPanel.add(buttonPanel, BorderLayout.CENTER)
            }

            mainPanel.revalidate()
            mainPanel.repaint()
        }

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                updateTreeWithSearch(searchField.text, treePanel, currentTree, isOmittedFilterActive) { newTree ->
                    currentTree = newTree
                    setupDoubleClickListener(newTree, project)
                }
            }

            override fun removeUpdate(e: DocumentEvent?) {
                updateTreeWithSearch(searchField.text, treePanel, currentTree, isOmittedFilterActive) { newTree ->
                    currentTree = newTree
                    setupDoubleClickListener(newTree, project)
                }
            }

            override fun changedUpdate(e: DocumentEvent?) {}
        })

        // Omitted Filter Button Listener
        omittedFilterButton.addActionListener {
            isOmittedFilterActive = omittedFilterButton.isSelected
            if (isOmittedFilterActive) {
                // Zeige alle Omitteds
                updateTreeWithSearch("", treePanel, currentTree, true) { newTree ->
                    currentTree = newTree
                    setupDoubleClickListener(newTree, project)
                }
            } else {
                // Zeige normalen Tree
                updateUI()
            }
        }

        // Refresh Button Listener
        refreshButton.addActionListener {
            println("Refreshing JSON...")
            parseJson(project)
            searchField.text = ""
            isOmittedFilterActive = false
            omittedFilterButton.isSelected = false
            updateUI()
        }

        updateUI()

        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}


private fun setupDoubleClickListener(tree: JTree, project: Project) {
    tree.addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent?) {
            if (e != null && e.clickCount == 2) {
                val path = tree.selectionPath ?: return
                val node = path.lastPathComponent as DefaultMutableTreeNode

                // Hole die Dependency direkt aus dem Node
                val dependency = node.userObject as? Dependency
                if (dependency != null) {
                    DependencyInfoDialog(project, dependency).show()
                }
            }
        }
    })
}

// Custom Renderer für die Dependency-Listen

fun findWinnerDependency(currNode: Dependency): Dependency? {
    val winnerName = currNode.winnerNodeName ?: return null

    println("DEBUG: Suche nach Winner für: ${currNode.artifactId}")
    println("DEBUG: winnerNodeName = '$winnerName'")

    var cleanedWinnerName = winnerName.replace(":jar:", ":")
    cleanedWinnerName = cleanedWinnerName.replace("?", "")

    println("DEBUG: cleanedWinnerName = '$cleanedWinnerName'")

    val result = allNodes.find { node ->
        !node.isOmitted && (node.name == cleanedWinnerName || "${node.groupId}:${node.artifactId}:${node.version} (${node.scope})" == cleanedWinnerName)
    }

    if (result != null) {
        println("DEBUG: Winner GEFUNDEN = ${result.artifactId} ${result.version}")
    } else {
        println("DEBUG: Winner NICHT gefunden")
        println("DEBUG: Suche nach ähnlichen Nodes:")
        allNodes.filter { !it.isOmitted && it.name.contains(cleanedWinnerName.substringBefore(":")) }.forEach {
            println("  - ${it.groupId}:${it.artifactId}:${it.version} (${it.scope})")
        }
    }

    return result
}

private fun createTreeRoot(dependency: Dependency): DefaultMutableTreeNode {
    val rootNode = DefaultMutableTreeNode(dependency)
    dependency.children.forEach { child ->
        rootNode.add(createTreeRoot(child))
    }
    return rootNode
}

private fun updateTreeWithSearch(
    searchTerm: String,
    treePanel: JPanel,
    currentTree: JTree?,
    isOmittedFilterActive: Boolean,
    updateTree: (JTree) -> Unit
) {
    // Wenn Omitted Filter aktiv, suche nur in Omitteds
    val sourceNodes = if (isOmittedFilterActive) {
        allNodes.filter { it.isOmitted }
    } else {
        allNodes
    }

    if (searchTerm.isBlank()) {
        if (dependencyTree != null) {
            if (isOmittedFilterActive) {
                // Zeige nur Omitted Dependencies als Flat List
                val rootNode = DefaultMutableTreeNode("Omitted Dependencies (${sourceNodes.size})")
                sourceNodes.forEach { dep ->
                    rootNode.add(DefaultMutableTreeNode(dep))
                }
                val tree = JTree(rootNode)
                tree.isRootVisible = true
                tree.expandRow(0)
                tree.cellRenderer = DependencyTreeCellRenderer()
                updateTree(tree)

                treePanel.removeAll()
                treePanel.add(JScrollPane(tree), BorderLayout.CENTER)
                treePanel.revalidate()
                treePanel.repaint()
            } else {
                // Normaler Tree
                val rootNode = createTreeRoot(dependencyTree!!)
                val tree = JTree(rootNode)
                tree.isRootVisible = true
                tree.expandRow(0)
                tree.cellRenderer = DependencyTreeCellRenderer()
                updateTree(tree)

                treePanel.removeAll()
                treePanel.add(JScrollPane(tree), BorderLayout.CENTER)
                treePanel.revalidate()
                treePanel.repaint()
            }
        }
        return
    }

    val matchingNodes = sourceNodes.filter {
        it.artifactId.contains(searchTerm, ignoreCase = true) || it.groupId.contains(searchTerm, ignoreCase = true)
    }

    if (matchingNodes.isEmpty()) {
        treePanel.removeAll()
        treePanel.add(JLabel("Keine Ergebnisse"), BorderLayout.CENTER)
        treePanel.revalidate()
        treePanel.repaint()
        return
    }

    val rootLabel = if (isOmittedFilterActive) "Omitted Search Results (${matchingNodes.size})" else "Suchergebnisse"
    val rootNode = DefaultMutableTreeNode(rootLabel)
    matchingNodes.forEach { matchedDep ->
        val node = DefaultMutableTreeNode(matchedDep)
        matchedDep.children.forEach { child ->
            node.add(createTreeRoot(child))
        }
        rootNode.add(node)
    }

    val tree = JTree(rootNode)
    tree.isRootVisible = true
    tree.expandRow(0)
    tree.cellRenderer = DependencyTreeCellRenderer()
    updateTree(tree)

    treePanel.removeAll()
    treePanel.add(JScrollPane(tree), BorderLayout.CENTER)
    treePanel.revalidate()
    treePanel.repaint()
}

fun buildLabel(dependency: Dependency): String {
    var label = "${dependency.groupId}:${dependency.artifactId}:${dependency.version} (${dependency.scope})"
    if (dependency.isOmitted) {
        label += " -OMITTED"
    }
    return label
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