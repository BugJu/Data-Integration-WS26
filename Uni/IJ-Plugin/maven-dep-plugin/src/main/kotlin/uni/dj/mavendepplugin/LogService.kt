package uni.dj.mavendepplugin

import com.google.gson.GsonBuilder
import com.intellij.openapi.project.Project
import java.io.File

object LogService {

    fun logChange(
        depChange: DepChange,
        project: Project,
    ): String? {

        val basePath = project.basePath ?: return null
        val targetDir = File(basePath, "target")

        try {
            // Verzeichnis erstellen, falls es nicht existiert
            if (!targetDir.exists()) {
                val created = targetDir.mkdirs()
                if (!created && !targetDir.exists()) {
                    println("ERROR: Could not create target directory: ${targetDir.absolutePath}")
                    return null
                }
                println("DEBUG: Created target directory: ${targetDir.absolutePath}")
            }

            val logFile = File(targetDir, "change-log.json")
            println("DEBUG: Log file path: ${logFile.absolutePath}")


            // ===== SCHREIBE JSON MIT DEBUGGING =====
            val gsonPretty = GsonBuilder().setPrettyPrinting().create()

            val logs = if (logFile.exists()) {
                try {
                    val content = logFile.readText(Charsets.UTF_8)
                    val type = object : com.google.gson.reflect.TypeToken<MutableList<DepChange>>() {}.type
                    gsonPretty.fromJson<MutableList<DepChange>>(content, type) ?: mutableListOf()
                } catch (e: Exception) {
                    println("DEBUG: Error reading log file, starting new: ${e.message}")
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }

            logs.add(depChange)
            val jsonString = gsonPretty.toJson(logs)

            // UTF-8 explizit setzen
            logFile.writeText(jsonString, Charsets.UTF_8)

            println("DEBUG: Successfully wrote log file!")
            return logFile.absolutePath
        } catch (e: Exception) {
            println("ERROR: Failed to log change: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}
