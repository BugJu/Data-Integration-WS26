package uni.dj.mavendepplugin

import com.google.gson.GsonBuilder
import com.intellij.openapi.project.Project
import java.io.File

object LogService {

    fun logChange(
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
}
