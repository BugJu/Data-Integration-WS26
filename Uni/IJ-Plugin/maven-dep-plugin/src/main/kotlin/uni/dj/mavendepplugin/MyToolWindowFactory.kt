package uni.dj.mavendepplugin

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
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
import java.io.File

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

                val rootNode = createTreeRoot(DependencyManager.dependencyTree!!)
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
                val treeRoot = DependencyManager.dependencyTree!!
                val totalDependencies = DependencyManager.allNodes.size - 1
                val directDeps = treeRoot.children.size
                val transitiveDeps = DependencyManager.allNodes.size - directDeps - 1
                val totalUnused = DependencyManager.allNodes.count { it.unused }
                val totalUnusedDirect = treeRoot.children.count { it.unused && it.children.isEmpty() }
                val totalUnusedTransitive = totalUnused - totalUnusedDirect
                val totalOmitted = DependencyManager.totalOmittedCount(treeRoot)
                val totalOmittedDirect = treeRoot.children.count { it.isOmitted }
                var totalOmittedTransitive = 0
                treeRoot.children.forEach { child ->
                    var list = child.children
                    list.forEach { grandChild ->
                        totalOmittedTransitive += DependencyManager.totalOmittedCount(
                            grandChild
                        )
                    }
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
                    val unusedNodes = DependencyManager.allNodes.filter { it.unused }

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
            DependencyManager.collectAllNodes(DependencyManager.dependencyTree!!)
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
        DependencyManager.allNodes.filter { it.isOmitted }
    } else {
        DependencyManager.allNodes
    }

    if (searchTerm.isBlank()) {
        if (DependencyManager.dependencyTree != null) {
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
                val rootNode = createTreeRoot(DependencyManager.dependencyTree!!)
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


private fun parseJson(project: Project) {
    val basePath = project.basePath ?: return
    val jsonDB = File(basePath, "target/dependency-tree.json")
    if (!jsonDB.exists()) return

    val jsonText = jsonDB.readText()
    val gson = Gson()
    DependencyManager.dependencyTree = gson.fromJson(jsonText, Dependency::class.java)
    DependencyManager.allNodes = DependencyManager.collectAllNodes(DependencyManager.dependencyTree!!)
    println("Alle Knoten: ${DependencyManager.allNodes.size}")
}

