package uni.dj.mavendepplugin

import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import java.io.File
import java.io.FileReader
import java.io.OutputStreamWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JOptionPane
import com.intellij.openapi.project.Project

object PomService {

    fun updatePomVersion(
        pomFile: File, groupId: String, artifactId: String, newVersion: String, deletionMode: Boolean = false
    ) {
        var modified = false
        try {
            val mavenReader = MavenXpp3Reader()
            val model = FileReader(pomFile).use { reader ->
                mavenReader.read(reader)
            }
            if (deletionMode) {
                println("if zweig")
                val iterator = model.dependencies.iterator()
                while (iterator.hasNext()) {
                    val dep = iterator.next()
                    if (dep.groupId == groupId && dep.artifactId == artifactId) {
                        iterator.remove()
                        modified = true
                    }
                }
            } else {
                println("else zweig")
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
            val errorMessage = e.message ?: e.toString()
            println("ERROR: Fehler beim Speichern von ${pomFile.absolutePath}: $errorMessage")
            e.printStackTrace()
            throw Exception("Fehler beim Speichern von ${pomFile.absolutePath}: $errorMessage", e)
        }
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
                val parentNode =
                    DependencyManager.allNodes.find { it.name + ":" + it.version + " (${it.scope})" == dependency.parent }
                        ?: throw IllegalStateException("Parent nicht gefunden!")

                val pomFile = File(parentNode.pathToDependencyPom)

                if (!pomFile.exists()) {
                    throw IllegalStateException("Pom von dieser Dependency konnte nicht gefunden werden: ${pomFile.absolutePath}")
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

                // ===== UPDATE POM =====
                updatePomVersion(
                    pomFile = pomFile,
                    groupId = dependency.groupId,
                    artifactId = dependency.artifactId,
                    newVersion = "",
                    deletionMode = true
                )

                val logPath = LogService.logChange(depChange, project)

                JOptionPane.showMessageDialog(
                    null,
                    "Dependency erfolgreich gelöscht:\n" + "${dependency.groupId}:${dependency.artifactId}\n\n" + "Log geschrieben zu:\n${logPath}",
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
    }

    fun changeDepToOmittedVersion(project: Project, dependency: Dependency) {
        val warningWindow = WarningConfirmDialog(
            project,
            "Be careful when changing to Omitted Version. This will change the POM. In the target folder there is a change-log.txt file where any changes are logged." + "Be sure to save it and/or use clean Maven argument to reload all dependencies.",
            "Change Version?"
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
                val winnerNode = DependencyManager.findWinnerDependency(dependency.winnerNodeName ?: "")
                    ?: throw IllegalStateException("Winner-Dependency nicht gefunden: ${dependency.winnerNodeName}")

                val winnerParent =
                    DependencyManager.findDirectParent(winnerNode)
                        ?: throw IllegalStateException("Parent des Winners nicht gefunden")

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
                val logPath = LogService.logChange(depChange, project)
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
}
