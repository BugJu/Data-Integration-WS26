package uni.dj.mavendepplugin

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.jetbrains.rd.generator.nova.PredefinedType
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode

import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import java.io.FileReader
import java.io.OutputStreamWriter
import kotlin.math.roundToInt

var allNodes = listOf<Dependency>()
var dependencyTree: Dependency? = null

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val basePath = project.basePath ?: return
        val dependencyTreeFile = File(basePath, "target/dependency-tree.json")

        val contentFactory = ContentFactory.getInstance()
        val mainPanel = JPanel(BorderLayout())

        // ===== OVERVIEW TAB =====
        val overviewPanel = JPanel(BorderLayout())
        val overviewContent = contentFactory.createContent(overviewPanel, "Overview", false)
        toolWindow.contentManager.addContent(overviewContent)

        // Erstelle die Tabelle
        val columnNames = arrayOf("Type", "Number", "Percentage")
        val data = arrayOf(
            arrayOf("Total Dependencies", 0, 0),
            arrayOf("Total Direct Dependencies", 0, 0),
            arrayOf("Total Transitive Dependencies", 0, 0),
            arrayOf("Total (potentially) unused Dependencies", 0, 0),
            arrayOf("Total (potentially) unused direct Dependencies", 0, 0),
            arrayOf("Total (potentially) unused transitive Dependencies", 0, 0),
            arrayOf("Total omitted Dependencies", 0, 0),
            arrayOf("Total omitted direct Dependencies ", 0, 0),
            arrayOf("Total omitted transitive Dependencies", 0, 0)
        )


        val model = object : DefaultTableModel(data, columnNames) {
            override fun isCellEditable(row: Int, column: Int) = false
        }


        val overviewTable = JBTable(model)
        overviewTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        overviewTable.isCellEditable(0, 0)

        val scrollPane = JBScrollPane(overviewTable)
        overviewPanel.add(scrollPane, BorderLayout.CENTER)


        // ===== DEPENDENCY MANAGER TAB =====
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

                // Update Overview Table
                val treeRoot = dependencyTree!!
                val totalDependencies = allNodes.size - 1
                val directDeps = treeRoot.children.size
                val transitiveDeps = allNodes.size - directDeps - 1
                val totalUnused = allNodes.count { it.unused }
                val totalUnusedDirect = treeRoot.children.count { it.unused && it.children.isEmpty() }
                val totalUnusedTransitive = totalUnused - totalUnusedDirect
                val totalOmitted = totalOmittedCount(treeRoot)
                val totalOmittedDirect = treeRoot.children.count { it.isOmitted }
                var totalOmittedTransitive = 0
                treeRoot.children.forEach { child ->
                    var list = child.children
                    list.forEach { grandChild -> totalOmittedTransitive += totalOmittedCount(grandChild) }
                }
                val listOfValues = mutableListOf(
                    totalDependencies, directDeps, transitiveDeps, totalUnused,
                    totalUnusedDirect, totalUnusedTransitive, totalOmitted,
                    totalOmittedDirect, totalOmittedTransitive
                )
                for (i in 0..<listOfValues.size) {
                    model.setValueAt(listOfValues[i], i, 1)
                    model.setValueAt(
                        "${"%.1f".format(listOfValues[i].toDouble() / totalDependencies.toDouble() * 100.0)}%",
                        i,
                        2
                    )
                }
            } else {
                val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER))
                val reloadButton = JButton("Reload")
                reloadButton.addActionListener {
                    updateUI()
                }
                buttonPanel.add(reloadButton)
                mainPanel.add(buttonPanel, BorderLayout.CENTER)
                overviewPanel.add(buttonPanel, BorderLayout.CENTER)
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
                updateTreeWithSearch("", treePanel, currentTree, true) { newTree ->
                    currentTree = newTree
                    setupDoubleClickListener(newTree, project)
                }
            } else {
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
            collectAllNodes(dependencyTree!!)
        }

        updateUI()

        val content = contentFactory.createContent(mainPanel, "Dependency Manager", false)
        toolWindow.contentManager.addContent(content)
    }
}

// Rest der Funktionen bleibt gleich...
private fun setupDoubleClickListener(tree: JTree, project: Project) {
    tree.addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent?) {
            if (e != null && e.clickCount == 2) {
                val path = tree.selectionPath ?: return
                val node = path.lastPathComponent as DefaultMutableTreeNode
                val dependency = node.userObject as? Dependency
                if (dependency != null) {
                    DependencyInfoDialog(project, dependency).show()
                }
            }
        }
    })
}

fun findWinnerDependency(currNodeName: String): Dependency? {
    val cleaned = currNodeName
        .replace(":jar:", ":")
        .replace("?", "")
        .trim()

    val parts = cleaned.split(":")
    if (parts.size < 3) {
        println("DEBUG: Ungültiges Format: $currNodeName")
        return null
    }

    val groupId = parts[0]
    val artifactId = parts[1]
    val version = parts.getOrNull(2)?.substringBefore(" ")?.trim() ?: ""

    val result = allNodes.find { node ->
        !node.isOmitted &&
                node.groupId == groupId &&
                node.artifactId == artifactId &&
                node.version == version
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
    val sourceNodes = if (isOmittedFilterActive) {
        allNodes.filter { it.isOmitted }
    } else {
        allNodes
    }

    if (searchTerm.isBlank()) {
        if (dependencyTree != null) {
            if (isOmittedFilterActive) {
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

fun changeDepToUsedVersion(dependency: Dependency) {
    try {
        if (!dependency.isOmitted) {
            throw IllegalStateException("Diese Dependency ist nicht omitted!")
        }

        if (dependency.winnerNodeName == null) {
            throw IllegalStateException("Keine Winner-Information vorhanden!")
        }

        val targetVersion = dependency.version
        val winnerNode = findWinnerDependency(dependency.winnerNodeName!!)
            ?: throw IllegalStateException("Winner-Dependency nicht gefunden: ${dependency.winnerNodeName}")

        val winnerParent = findDirectParent(winnerNode)
            ?: throw IllegalStateException("Parent des Winners nicht gefunden")

        val pomFile = File(winnerParent.pathToDependencyPom)
        if (!pomFile.exists()) {
            throw IllegalStateException("POM-Datei nicht gefunden: ${pomFile.absolutePath}")
        }

        updatePomVersion(
            pomFile = pomFile,
            groupId = dependency.groupId,
            artifactId = dependency.artifactId,
            newVersion = targetVersion
        )

        JOptionPane.showMessageDialog(
            null,
            "Version erfolgreich geändert:\n" +
                    "${dependency.groupId}:${dependency.artifactId}\n" +
                    "Von: ${winnerNode.version} (Winner)\n" +
                    "Zu: $targetVersion (Omitted)",
            "Erfolg",
            JOptionPane.INFORMATION_MESSAGE
        )

    } catch (e: Exception) {
        e.printStackTrace()
        JOptionPane.showMessageDialog(
            null,
            "Fehler: ${e.message}",
            "Fehler",
            JOptionPane.ERROR_MESSAGE
        )
    }
}

private fun updatePomVersion(
    pomFile: File,
    groupId: String,
    artifactId: String,
    newVersion: String
) {
    try {
        val mavenReader = MavenXpp3Reader()
        val model = FileReader(pomFile).use { reader ->
            mavenReader.read(reader)
        }

        var modified = false
        model.dependencies?.forEach { dep ->
            if (dep.groupId == groupId && dep.artifactId == artifactId) {
                val oldVersion = dep.version
                dep.version = newVersion
                modified = true
                println("DEBUG: Ändere $groupId:$artifactId von $oldVersion zu $newVersion")
            }
        }

        if (!modified) {
            println("WARN: Dependency nicht im POM gefunden: $groupId:$artifactId")
            return
        }

        val mavenWriter = MavenXpp3Writer()
        val outputStream = pomFile.outputStream().buffered()
        val writer = OutputStreamWriter(outputStream, "UTF-8")

        try {
            mavenWriter.write(writer, model)
            writer.flush()
            outputStream.flush()
        } finally {
            writer.close()
            outputStream.close()
        }

        println("DEBUG: Erfolgreich gespeichert: ${pomFile.absolutePath}")
        verifyPomWasWritten(pomFile, groupId, artifactId, newVersion)

    } catch (e: Exception) {
        println("ERROR: Fehler beim Speichern von ${pomFile.absolutePath}: ${e.message}")
        e.printStackTrace()
        throw e
    }
}

private fun verifyPomWasWritten(
    pomFile: File,
    groupId: String,
    artifactId: String,
    expectedVersion: String
) {
    try {
        val mavenReader = MavenXpp3Reader()
        val model = FileReader(pomFile).use { reader ->
            mavenReader.read(reader)
        }

        val found = model.dependencies?.find { dep ->
            dep.groupId == groupId && dep.artifactId == artifactId
        }

        if (found != null && found.version == expectedVersion) {
            println("✓ VERIFIZIERUNG OK: $groupId:$artifactId ist jetzt auf $expectedVersion")
        }
    } catch (e: Exception) {
        println("✗ VERIFIZIERUNG ERROR: ${e.message}")
    }
}

private fun findDirectParent(currNode: Dependency): Dependency? {
    return allNodes.find { node ->
        node.children.any { child ->
            child.groupId == currNode.groupId &&
                    child.artifactId == currNode.artifactId &&
                    child.version == currNode.version
        }
    }
}

private fun totalOmittedCount(node: Dependency?): Int {
    return countOmittedRecursive(node)
}

private fun countOmittedRecursive(node: Dependency?): Int {
    if (node == null) return 0
    var count = if (node.isOmitted) 1 else 0
    node.children.forEach { child ->
        count += countOmittedRecursive(child)
    }
    return count
}
