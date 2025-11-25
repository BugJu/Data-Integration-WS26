package uni.dj.mavendepplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*

class DependencyInfoDialog(private val project: Project, private val dependency: Dependency) : DialogWrapper(project) {

    init {
        title = "Dependency Details: ${dependency.artifactId}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val infoPanel = JPanel(GridLayout(0, 2, 5, 5))
        infoPanel.add(JLabel("Group ID:"))
        infoPanel.add(JLabel(dependency.groupId))
        infoPanel.add(JLabel("Artifact ID:"))
        infoPanel.add(JLabel(dependency.artifactId))
        infoPanel.add(JLabel("Version:"))
        infoPanel.add(JLabel(dependency.version))
        infoPanel.add(JLabel("Scope:"))
        infoPanel.add(JLabel(dependency.scope))
        infoPanel.add(JLabel("path to Dependency:"))
        infoPanel.add(JLabel(dependency.pathToDependencyPom))
        // Tree-Label anzeigen (mit -OMITTED wenn nötig)
        infoPanel.add(JLabel("Tree Label:"))
        infoPanel.add(JLabel(buildLabel(dependency)))

        infoPanel.add(JLabel("Omitted:"))
        infoPanel.add(JLabel(dependency.isOmitted.toString()))
        // TODO: Maybe add button to delete versioning in parent node

        //TODO: add something to see beter how much is omitted and how much is potentially unused
        infoPanel.add(JLabel("(potentially) unused:"))
        infoPanel.add(JLabel(dependency.unused.toString()))

        val copyPathToClipboardButton = JButton("Copy Path to Dependency")
        val fileToDependency = File(dependency.pathToDependencyPom)
        val vf = LocalFileSystem.getInstance().findFileByIoFile(fileToDependency)
        copyPathToClipboardButton.addActionListener {
            println(dependency.pathToDependency)
            print(fileToDependency.absolutePath)
            if (vf != null) {
                RevealFileAction.openFile(fileToDependency)
            }
        }
        infoPanel.add(copyPathToClipboardButton)

        if (dependency.isOmitted && dependency.winnerNodeName != null) {
            val winnerButton = JButton("Open path to Dependency: " + dependency.winnerNodeName)
            winnerButton.addActionListener {
                openWinnerDependencyDialog()
            }
            infoPanel.add(winnerButton)
            val useOmittedVersionButton = JButton("Use Omitted Version")
            useOmittedVersionButton.addActionListener { changeDepToUsedVersion(dependency) }
            useOmittedVersionButton.preferredSize = Dimension(70, 30)
            infoPanel.add(useOmittedVersionButton, BorderLayout.EAST)
        }

        val parentsList = findAllParents(dependency)

        val parentPanel = JPanel(BorderLayout(5, 5))
        val parentLabel = JLabel("Parent Dependencies: (${parentsList.size})")
        parentPanel.add(parentLabel, BorderLayout.NORTH)
        Collections.reverse(parentsList)

        if (parentsList.isNotEmpty()) {
            val parentModel = DefaultListModel<Dependency>()
            parentsList.forEach { parent ->
                parentModel.addElement(parent)
            }
            val parentList = JBList(parentModel)
            parentList.cellRenderer = DependencyListCellRenderer()

            // Double-Click auf Parent öffnet neuen Dialog
            parentList.addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent?) {
                    if (e != null && e.clickCount == 2) {
                        val index = parentList.selectedIndex
                        if (index >= 0) {
                            val selectedParent = parentModel.getElementAt(index)
                            DependencyInfoDialog(project, selectedParent).show()
                        }
                    }
                }
            })

            parentPanel.add(JScrollPane(parentList), BorderLayout.CENTER)
        } else {
            parentPanel.add(JLabel("Keine Parent Dependencies"), BorderLayout.CENTER)
        }

        // ===== CHILDREN DEPENDENCIES SECTION =====
        val dependenciesPanel = JPanel(BorderLayout(5, 5))
        val dependencyLabel = JLabel("Dependencies: (${dependency.children.size})")
        dependenciesPanel.add(dependencyLabel, BorderLayout.NORTH)

        if (dependency.children.isNotEmpty()) {
            val childModel = DefaultListModel<Dependency>()
            dependency.children.forEach { child ->
                childModel.addElement(child)
            }
            val childList = JBList(childModel)
            childList.cellRenderer = DependencyListCellRenderer()

            // Double-Click auf Child öffnet neuen Dialog
            childList.addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent?) {
                    if (e != null && e.clickCount == 2) {
                        val index = childList.selectedIndex
                        if (index >= 0) {
                            val selectedChild = childModel.getElementAt(index)
                            DependencyInfoDialog(project, selectedChild).show()
                        }
                    }
                }
            })

            dependenciesPanel.add(JScrollPane(childList), BorderLayout.CENTER)
        } else {
            dependenciesPanel.add(JLabel("Keine Dependencies"), BorderLayout.CENTER)
        }

        // ===== LAYOUT =====
        val tabPanel = JBTabbedPane()
        tabPanel.addTab("Info", infoPanel)
        tabPanel.addTab("Parents", parentPanel)
        tabPanel.addTab("Children", dependenciesPanel)

        panel.add(tabPanel, BorderLayout.CENTER)

        return panel
    }

    private fun findDirectParent(currNode: Dependency): Dependency? {
        return allNodes.find { node -> node.children.contains(currNode) }
    }

    private fun findAllParents(
        currNode: Dependency, visited: MutableSet<String> = mutableSetOf()
    ): List<Dependency> {
        val parents = mutableListOf<Dependency>()
        val nodeId = "${currNode.groupId}:${currNode.artifactId}:${currNode.version}"

        // Verhindere zirkuläre Abhängigkeiten
        if (nodeId in visited) {
            return parents
        }
        visited.add(nodeId)

        val directParent = findDirectParent(currNode)
        if (directParent != null) {
            parents.add(directParent)
            // Rekursiv die Parents des Parents suchen
            parents.addAll(findAllParents(directParent, visited))
        }

        return parents
    }

    private fun openWinnerDependencyDialog() {
        val depWinner = dependency.winner ?: throw IllegalStateException("Winner Dependency is null")
        val winnerDep = findWinnerDependency(dependency.winner)
        if (winnerDep != null) {
            DependencyInfoDialog(project, winnerDep).show()
        } else {
            JOptionPane.showMessageDialog(
                null,
                "Winner Dependency nicht gefunden: ${dependency.winnerNodeName}",
                "Fehler",
                JOptionPane.WARNING_MESSAGE
            )
        }
    }

    override fun getDimensionServiceKey(): String {
        return "DependencyInfoDialog"
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return null
    }
}

