package uni.dj.mavendepplugin

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode

import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStreamWriter

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
            collectAllNodes(dependencyTree!!)
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

fun findWinnerDependency(currNodeName: String): Dependency? {
    // Parse Winner-String: "groupId:artifactId:jar:version (scope)"
    val cleaned = currNodeName
        .replace(":jar:", ":")
        .replace("?", "")
        .trim()

    // Extrahiere Komponenten
    val parts = cleaned.split(":")
    if (parts.size < 3) {
        println("DEBUG: Ungültiges Format: $currNodeName")
        return null
    }

    val groupId = parts[0]
    val artifactId = parts[1]
    val version = parts.getOrNull(2)?.substringBefore(" ")?.trim() ?: ""

    println("DEBUG: Suche Winner - groupId=$groupId, artifactId=$artifactId, version=$version")

    // Exakte Suche nach groupId:artifactId:version
    val result = allNodes.find { node ->
        !node.isOmitted &&
                node.groupId == groupId &&
                node.artifactId == artifactId &&
                node.version == version
    }

    if (result != null) {
        println("DEBUG: Winner GEFUNDEN = ${result.artifactId} ${result.version}")
    } else {
        println("DEBUG: Winner NICHT gefunden für $groupId:$artifactId:$version")
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


fun changeDepToUsedVersion(dependency: Dependency) {
    try {
        if (!dependency.isOmitted) {
            throw IllegalStateException("Diese Dependency ist nicht omitted!")
        }

        if (dependency.winnerNodeName == null) {
            throw IllegalStateException("Keine Winner-Information vorhanden!")
        }

        // 1. Die ZIEL-Version ist die OMITTED-Version (die gewünschte)
        val targetVersion = dependency.version

        // 2. Finde den Winner-Node (der aktuell genutzt wird)
        val winnerNode = findWinnerDependency(dependency.winnerNodeName!!)
            ?: throw IllegalStateException("Winner-Dependency nicht gefunden: ${dependency.winnerNodeName}")

        println("DEBUG: Ändere ${dependency.groupId}:${dependency.artifactId}")
        println("DEBUG: Von Winner-Version ${winnerNode.version} zu Omitted-Version $targetVersion")

        // 3. Finde das POM, das den WINNER deklariert (das müssen wir ändern)
        val winnerParent = findDirectParent(winnerNode)
            ?: throw IllegalStateException("Parent des Winners nicht gefunden")

        val pomFile = File(winnerParent.pathToDependencyPom)
        if (!pomFile.exists()) {
            throw IllegalStateException("POM-Datei nicht gefunden: ${pomFile.absolutePath}")
        }

        println("DEBUG: Bearbeite POM: ${pomFile.absolutePath}")
        println("DEBUG: Winner-Parent: ${winnerParent.artifactId} ${winnerParent.version}")

        // 4. Update das POM
        updatePomVersion(
            pomFile = pomFile,
            groupId = dependency.groupId,
            artifactId = dependency.artifactId,
            newVersion = targetVersion  // ← Die omitted Version!
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


private fun extractVersionFromWinner(winnerString: String): String {
    // Parse "groupId:artifactId:jar:version (scope)"
    val cleaned = winnerString.replace(":jar:", ":").substringBefore(" (")
    val parts = cleaned.split(":")
    return parts.getOrNull(2)?.trim() ?: ""
}

private fun updatePomVersion(
    pomFile: File,
    groupId: String,
    artifactId: String,
    newVersion: String
) {
    println("DEBUG: Bearbeite POM: ${pomFile.absolutePath}")

    try {
        // 1. POM einlesen
        val mavenReader = MavenXpp3Reader()
        val model = FileReader(pomFile).use { reader ->
            mavenReader.read(reader)
        }

        // 2. Dependency finden und ändern
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

        // 3. WICHTIG: Mit OutputStreamWriter und explizitem Encoding schreiben
        val mavenWriter = MavenXpp3Writer()
        val outputStream = pomFile.outputStream().buffered()
        val writer = OutputStreamWriter(outputStream, "UTF-8")

        try {
            mavenWriter.write(writer, model)
            writer.flush()  // ✅ WICHTIG: Explizit flushen
            outputStream.flush()
        } finally {
            writer.close()
            outputStream.close()
        }

        println("DEBUG: Erfolgreich gespeichert: ${pomFile.absolutePath}")

        // 4. Verifizierung: Lese die Datei nochmal, um zu checken
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

        if (found != null) {
            if (found.version == expectedVersion) {
                println("✓ VERIFIZIERUNG OK: $groupId:$artifactId ist jetzt auf $expectedVersion")
            } else {
                println("✗ VERIFIZIERUNG FAILED: $groupId:$artifactId ist auf ${found.version}, erwartet: $expectedVersion")
            }
        } else {
            println("✗ VERIFIZIERUNG FAILED: Dependency nicht im POM gefunden")
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

