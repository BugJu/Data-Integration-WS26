package uni.dj.mavendepplugin

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.File
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import javax.swing.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import java.io.FileReader
import java.io.OutputStreamWriter
import kotlin.printStackTrace

var allNodes = listOf<Dependency>()
var dependencyTree: Dependency? = null

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val basePath = project.basePath ?: return
        val dependencyTreeFile = File(basePath, "target/dependency-tree.json")
        val contentFactory = ContentFactory.getInstance()

        // ===== TAB 1: OVERVIEW =====
        val overviewPanel = JPanel(BorderLayout())
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

        val overviewScrollPane = JBScrollPane(overviewTable)
        overviewPanel.add(overviewScrollPane, BorderLayout.CENTER)

        val overviewContent = contentFactory.createContent(overviewPanel, "Overview", false)
        toolWindow.contentManager.addContent(overviewContent)

        // ===== TAB 2: DEPENDENCY MANAGER =====
        val refreshButton = JButton("Refresh JSON")
        val filterLaberForComboBox = JBLabel("Filter:")
        val filters = arrayOf("------", "Omitted", "Unused")
        val filterComoboBox = ComboBox(filters)

        var isOmittedFilterActive = false

        val searchPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val searchField = JTextField(20)
        searchField.preferredSize = Dimension(200, 30)
        searchPanel.add(JLabel("Suche:"))
        searchPanel.add(searchField)
        searchPanel.add(refreshButton)
        searchPanel.add(filterLaberForComboBox)
        searchPanel.add(filterComoboBox)

        val treePanel = JPanel(BorderLayout())
        var currentTree: JTree? = null

        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(searchPanel, BorderLayout.NORTH)
        mainPanel.add(treePanel, BorderLayout.CENTER)

        fun updateUI() {
            if (dependencyTreeFile.exists()) {
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
                    totalDependencies,
                    directDeps,
                    transitiveDeps,
                    totalUnused,
                    totalUnusedDirect,
                    totalUnusedTransitive,
                    totalOmitted,
                    totalOmittedDirect,
                    totalOmittedTransitive
                )
                for (i in 0..<listOfValues.size) {
                    model.setValueAt(listOfValues[i], i, 1)
                    model.setValueAt(
                        "${"%.1f".format(listOfValues[i].toDouble() / totalDependencies.toDouble() * 100.0)}%", i, 2
                    )
                }
            }
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

        filterComoboBox.addActionListener {
            when (filterComoboBox.selectedItem) {
                filters[0] -> {
                    // "------" selected - show all
                    isOmittedFilterActive = false
                    searchField.text = ""
                    updateUI()
                }

                filters[1] -> {
                    // "Omitted" selected
                    isOmittedFilterActive = true
                    searchField.text = ""
                    updateTreeWithSearch("", treePanel, currentTree, true) { newTree ->
                        currentTree = newTree
                        setupDoubleClickListener(newTree, project)
                    }
                }

                filters[2] -> {
                    // "Unused" selected
                    isOmittedFilterActive = false
                    searchField.text = ""
                    // Filter für "unused" Dependencies
                    val unusedNodes = allNodes.filter { it.unused }

                    if (unusedNodes.isEmpty()) {
                        treePanel.removeAll()
                        treePanel.add(JLabel("Keine Ergebnisse"), BorderLayout.CENTER)
                        treePanel.revalidate()
                        treePanel.repaint()
                        return@addActionListener
                    }

                    val rootNode = DefaultMutableTreeNode("Unused Dependencies (${unusedNodes.size})")
                    unusedNodes.forEach { dep ->
                        rootNode.add(DefaultMutableTreeNode(dep))
                    }
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
                }
            }
        }


        refreshButton.addActionListener {
            println("Refreshing JSON...")
            parseJson(project)
            searchField.text = ""
            isOmittedFilterActive = false
            updateUI()
            collectAllNodes(dependencyTree!!)
        }

        updateUI()

        val managerContent = contentFactory.createContent(mainPanel, "Dependency Manager", false)
        toolWindow.contentManager.addContent(managerContent)

        val graphPanel = JPanel(BorderLayout())
        addGraphTabToPanel(graphPanel, basePath + "/target/dependency-tree.png")

        val graphContent = contentFactory.createContent(graphPanel, "Dependency Graph", false)
        toolWindow.contentManager.addContent(graphContent)
    }
}

fun addGraphTabToPanel(panel: JPanel, imagePath: String) {
    try {
        val originalImage: BufferedImage = ImageIO.read(File(imagePath))
        var currentImage = originalImage
        var scale = 1.0

        val mainPanel = JPanel(BorderLayout())

        // Image Label in ScrollPane
        val imageLabel = JLabel(ImageIcon(currentImage))
        val scrollPane = JBScrollPane(imageLabel).apply {
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        val zoomLabel = JLabel("100%")

        scrollPane.addMouseWheelListener { e ->

            val notches = e.wheelRotation
            val zoomFactor = 1.05

            if (notches < 0) {
                scale *= zoomFactor
            } else if (notches > 0 && scale > 0.2) {
                scale /= zoomFactor
            }
            currentImage = scaleImage(originalImage, scale)
            imageLabel.icon = ImageIcon(currentImage)
            imageLabel.revalidate()
            imageLabel.repaint()
            zoomLabel.text = "${"%.0f".format(scale * 100)}%"
            e.consume()
        }

        // Toolbar mit Zoom-Buttons
        val toolbar = JPanel().apply {
            add(JButton("🔍+").apply {
                addActionListener {
                    scale *= 1.2  // 20% Zoom in
                    currentImage = scaleImage(originalImage, scale)
                    imageLabel.icon = ImageIcon(currentImage)
                    imageLabel.revalidate()
                    imageLabel.repaint()
                    println("Zoomed in: ${"%.0f".format(scale * 100)}%")
                }
            })
            add(JButton("🔍-").apply {
                addActionListener {
                    if (scale > 0.2) {  // Minimum Zoom 20%
                        scale /= 1.2  // 20% Zoom out
                        currentImage = scaleImage(originalImage, scale)
                        imageLabel.icon = ImageIcon(currentImage)
                        imageLabel.revalidate()
                        imageLabel.repaint()
                        println("Zoomed out: ${"%.0f".format(scale * 100)}%")
                    }
                }
            })
            add(JButton("Fit").apply {
                addActionListener {
                    scale = 1.0
                    currentImage = originalImage
                    imageLabel.icon = ImageIcon(currentImage)
                    imageLabel.revalidate()
                    imageLabel.repaint()
                    println("Fit to original")
                }
            })
            add(JLabel("Zoom: 100%").apply {
                // Optional: Zoom-Level anzeigen
            })
        }

        mainPanel.add(toolbar, BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        panel.add(mainPanel, BorderLayout.CENTER)
    } catch (e: Exception) {
        panel.add(JLabel("Error loading image: ${e.message}"), BorderLayout.CENTER)
    }
}

private fun scaleImage(originalImage: BufferedImage, scale: Double): BufferedImage {
    val newWidth = (originalImage.width * scale).toInt()
    val newHeight = (originalImage.height * scale).toInt()

    if (newWidth <= 0 || newHeight <= 0) return originalImage

    val scaledImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
    val g2d = scaledImage.createGraphics()
    g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null)
    g2d.dispose()
    return scaledImage
}

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
    val cleaned = currNodeName.replace(":jar:", ":").replace("?", "").trim()

    val parts = cleaned.split(":")
    if (parts.size < 3) {
        println("DEBUG: Ungültiges Format: $currNodeName")
        return null
    }

    val groupId = parts[0]
    val artifactId = parts[1]
    val version = parts.getOrNull(2)?.substringBefore(" ")?.trim() ?: ""

    val result = allNodes.find { node ->
        !node.isOmitted && node.groupId == groupId && node.artifactId == artifactId && node.version == version
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

fun deleteUnusedDependency(
    project: Project, dependency: Dependency
) {
    val warningWindow = WarningConfirmDialog(
        project,
        "Be careful when deleting unused Dependencies. Unused Dependency might be a False Positive" + "You are actively changing the POM. In the target folder there is a change-log.txt file where any changes are logged." + "Be sure to save it and/or use clean Maven argument to reload all dependencies.",
        "Dependency delete?"
    )
    if (warningWindow.showAndGet()) {
        try {
            if (!dependency.unused) {
                throw IllegalStateException("Diese Dependency ist nicht unused!")
            }
            dependency.parent ?: throw IllegalStateException("Keine Parent gefunden!")
            val cleanedName = dependency.name + ":" + dependency.version + " (${dependency.scope})"
            println(cleanedName + ", " + dependency.parent)
            val parentNode = allNodes.find { it.name + ":" + it.version + " (${it.scope})" == dependency.parent }
                ?: throw IllegalStateException("Parent nicht gefunden!")

            if (!File(dependency.pathToDependencyPom).exists()) {
                throw IllegalStateException("Pom von dieser Dependency konnte nicht gefunden werden")
            }
            val depChange = DepChange(
                oldGroupID = dependency.groupId,
                oldArtifactID = dependency.artifactId,
                oldVersion = dependency.version,
                oldName = dependency.name,
                newGroupID = "none",
                newArtifactID = "none",
                newName = "none",
                newVersion = "none",
                operationTye = "deleted unused (old) Version of Dependency",
                date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
            )
            updatePomVersion(
                File(parentNode.pathToDependencyPom), dependency.groupId, dependency.artifactId, "none", true
            )
            val logPath = logChange(depChange, project)
            JOptionPane.showMessageDialog(
                null,
                "Version erfolgreich gelöscht:\n" + "${dependency.groupId}:${dependency.artifactId}\n" + "Log geschrieben zu:\n${logPath}",
                "Erfolg",
                JOptionPane.INFORMATION_MESSAGE
            )
        } catch (e: Exception) {
            e.printStackTrace()
            println("ERROR: ${e.stackTraceToString()}")

            JOptionPane.showMessageDialog(
                null,
                "Fehler: ${e.message}\n\nStackTrace:\n${e.stackTraceToString()}",
                "Fehler",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

}


fun changeDepToOmittedVersion(project: Project, dependency: Dependency) {
    val warningWindow = WarningConfirmDialog(
        project,
        "Be careful when changing Version." + "You are actively changing the POM. In the target folder there is a change-log.txt file where any changes are logged." + "Be sure to save it and/or use clean Maven argument to reload all dependencies.",
        "Dependency Version ändern?"
    )
    if (warningWindow.showAndGet()) {
        try {
            if (!dependency.isOmitted) {
                throw IllegalStateException("Diese Dependency ist nicht omitted!")
            }

            if (dependency.winnerNodeName == null) {
                throw IllegalStateException("Keine Winner-Information vorhanden!")
            }

            val targetVersion = dependency.version
            val winnerNode = findWinnerDependency(dependency.winnerNodeName)
                ?: throw IllegalStateException("Winner-Dependency nicht gefunden: ${dependency.winnerNodeName}")

            val winnerParent =
                findDirectParent(winnerNode) ?: throw IllegalStateException("Parent des Winners nicht gefunden")

            val pomFile = File(winnerParent.pathToDependencyPom)
            if (!pomFile.exists()) {
                throw IllegalStateException("POM-Datei nicht gefunden: ${pomFile.absolutePath}")
            }

            val depChange = DepChange(
                newGroupID = dependency.groupId,
                newArtifactID = dependency.artifactId,
                newVersion = targetVersion,
                oldGroupID = winnerNode.groupId,
                newName = dependency.name,
                oldArtifactID = winnerNode.artifactId,
                oldName = winnerNode.name,
                oldVersion = winnerNode.version,
                operationTye = "changed Dependency from old version to new version",
                date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
            )

            // ===== UPDATE POM =====
            updatePomVersion(
                pomFile = pomFile,
                groupId = dependency.groupId,
                artifactId = dependency.artifactId,
                newVersion = targetVersion,
                deletionMode = false
            )
            val logPath = logChange(depChange, project)
            JOptionPane.showMessageDialog(
                null,
                "Version erfolgreich geändert:\n" + "${dependency.groupId}:${dependency.artifactId}\n" + "Von: ${winnerNode.version} (Winner)\n" + "Zu: $targetVersion (Omitted)\n\n" + "Log geschrieben zu:\n${logPath}",
                "Erfolg",
                JOptionPane.INFORMATION_MESSAGE
            )

        } catch (e: Exception) {
            e.printStackTrace()
            println("ERROR: ${e.stackTraceToString()}")

            JOptionPane.showMessageDialog(
                null,
                "Fehler: ${e.message}\n\nStackTrace:\n${e.stackTraceToString()}",
                "Fehler",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
}

private fun logChange(
    depChange: DepChange,
    project: Project,
): String {

    val basePath = project.basePath ?: throw IllegalStateException("Project basePath ist null!")
    val targetDir = File(basePath, "target")

    // Verzeichnis erstellen, falls es nicht existiert
    if (!targetDir.exists()) {
        targetDir.mkdirs()
        println("DEBUG: Created target directory: ${targetDir.absolutePath}")
    }

    val logFile = File(targetDir, "change-log.txt")
    println("DEBUG: Log file path: ${logFile.absolutePath}")


    // ===== SCHREIBE JSON MIT DEBUGGING =====
    val gsonPretty = GsonBuilder().setPrettyPrinting().create()
    val jsonString = gsonPretty.toJson(depChange)

    // UTF-8 explizit setzen
    logFile.appendText(jsonString, Charsets.UTF_8)

    println("DEBUG: Successfully wrote log file!")
    println("DEBUG: File exists after write: ${logFile.exists()}")
    println("DEBUG: File size: ${logFile.length()} bytes")

    return logFile.absolutePath
}


private fun updatePomVersion(
    pomFile: File, groupId: String, artifactId: String, newVersion: String, deletionMode: Boolean = false
) {
    var modified = false
    try {
        val mavenReader = MavenXpp3Reader()
        val model = FileReader(pomFile).use { reader ->
            mavenReader.read(reader)
        }
        if (deletionMode) {
            model.dependencies.forEach { dep ->
                if (dep.groupId == groupId && dep.artifactId == artifactId) {
                    model.dependencies?.remove(dep)
                    modified = true
                }
            }

        } else {
            model.dependencies?.forEach { dep ->
                if (dep.groupId == groupId && dep.artifactId == artifactId) {
                    val oldVersion = dep.version
                    dep.version = newVersion
                    modified = true
                    println("DEBUG: Ändere $groupId:$artifactId von $oldVersion zu $newVersion")
                }
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

    } catch (e: Exception) {
        println("ERROR: Fehler beim Speichern von ${pomFile.absolutePath}: ${e.message}")
        e.printStackTrace()
        throw e
    }
}


private fun findDirectParent(currNode: Dependency): Dependency? {
    return allNodes.find { node ->
        node.children.any { child ->
            child.groupId == currNode.groupId && child.artifactId == currNode.artifactId && child.version == currNode.version
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
        val iconLabel = JBLabel(AllIcons.General.Warning).apply {
            verticalAlignment = JLabel.TOP
        }
        contentPanel.add(iconLabel, BorderLayout.WEST)

        // Message TextArea
        val textArea = JTextArea(message).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = JBLabel().font
            foreground = UIManager.getColor("Label.foreground")
        }

        val scrollPane = JBScrollPane(textArea).apply {
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = Dimension(350, 150)
        }

        contentPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(contentPanel, BorderLayout.CENTER)

        return mainPanel
    }
}

