package uni.dj

import org.opalj.br.analyses.Project
import org.opalj.br.instructions._
import java.io.File

object OpalSecurityAnalyzer {

  def analyzeJar(jarFile: File): String = {
    try {
      val project = Project(jarFile)  // jetzt korrekter Import
      
      val sb = new StringBuilder()
      sb.append(s"\n📦 File: ${jarFile.getName}\n")
      sb.append("=" * 70 + "\n")

      var criticalCount = 0
      var highCount = 0

      for (classFile <- project.allClassFiles) {
        val className = classFile.thisType.toJava

        for (method <- classFile.methods) {
          if (method.body.isDefined) {
            val body = method.body.get
            val instructions = body.instructions

            for (i <- 0 until instructions.length if instructions(i) != null) {
             instructions(i) match {
  case INVOKEVIRTUAL(receiverType, "exec", _) if receiverType.toJava == "java.lang.Runtime" =>
    sb.append(s"[CRITICAL] Runtime.exec() in $className.${method.name}\n")
    criticalCount += 1

  case INVOKEVIRTUAL(receiverType, "readObject", _) if receiverType.toJava == "java.io.ObjectInputStream" =>
    sb.append(s"[CRITICAL] Unsafe Deserialization in $className.${method.name}\n")
    criticalCount += 1

  case INVOKEVIRTUAL(receiverType, methodName @ ("execute" | "executeQuery" | "executeUpdate"), _) if receiverType.toJava.contains("Statement") =>
    sb.append(s"[HIGH] SQL Execution in $className.${method.name}\n")
    highCount += 1

  case INVOKESTATIC(receiverType, isInterface, "getInstance", _) if receiverType.toJava == "java.security.MessageDigest" =>
    sb.append(s"[HIGH] Weak Hash (MessageDigest) in $className.${method.name}\n")
    highCount += 1

  case _ => // Ignore other instructions
}

            }
          }
        }
      }

      sb.append("=" * 70 + "\n")
      sb.append(s"Summary: $criticalCount CRITICAL, $highCount HIGH\n")

      sb.toString()

    } catch {
      case e: Exception =>
        s"❌ Error analyzing ${jarFile.getName}: ${e.getMessage}\n"
    }
  }
}
