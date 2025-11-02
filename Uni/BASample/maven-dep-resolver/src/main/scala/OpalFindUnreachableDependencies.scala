package uni.dj

import com.typesafe.config.ConfigValueFactory
import org.opalj.br.BaseConfig
import org.opalj.br.analyses.Project
import org.opalj.br.reader.Java17Framework
import org.opalj.tac.cg.{CHACallGraphKey, RTACallGraphKey, XTACallGraphKey}
import org.opalj.tac.cg.CallGraph
import scala.util.control.Breaks._


import java.io.FileWriter
import java.io.File
import java.net.URL
import java.nio.file.{FileSystems, Files, Path}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.IteratorHasAsScala

object OpalFindUnreachableDependencies {

  def findUnreachableDependencies(classFileDirectory: String,
                                  logger: MavenLogger,
                                  libraryFiles: ListBuffer[File]
                                 ): CallGraph = {
    logger.info("Loading project...")
    val libraryFilesArray: Array[File] = libraryFiles.toArray[File]
    val project = loadProject(classFileDirectory, logger, libraryFilesArray)
    logger.info(project.libraryClassFilesCount.toString)
    val cg = getCallGraph(project, logger, 2)
    cg
  }

  private def loadProject(
                           classFileDirectory: String,
                           logger: MavenLogger,
                           libraryFiles: Array[File]
                         ): Project[URL] = {
    logger.info("Building project...")
    val classes = ListBuffer[File]()
    val classFiles = Files
      .walk(FileSystems.getDefault.getPath(classFileDirectory))
      .iterator()
      .asScala
      .filter(path => path.toString.endsWith(".class"))
      .toSeq
    classFiles.foreach(path => classes += new File(path.toString))
    val projectClassFiles = Java17Framework.AllClassFiles(classes)
    val libraryClassFiles = Java17Framework.AllClassFiles(libraryFiles)
    logger.info(libraryClassFiles.toString())
    var config = BaseConfig

    Project(projectClassFiles, libraryClassFiles,
      libraryClassFilesAreInterfacesOnly = false,
      virtualClassFiles = Iterable.empty)(config)
  }

  private def getCallGraph(project: Project[URL], logger: MavenLogger, typ: Int): CallGraph = {
    var cg: CallGraph = null
    typ match {
      case 1 => cg = project.get(CHACallGraphKey)
      case 2 => cg = project.get(RTACallGraphKey)
      case 3 => cg = project.get(XTACallGraphKey)
      case _ => logger.error("Invalid call graph type")
    }
    cg
  }

  import java.io.FileWriter
  import org.opalj.tac.cg.CallGraph

  def exportCallGraphToDot(
                            callGraph: CallGraph,
                            outputDirectory: File, // target-Verzeichnis
                            outputFileName: String = "callgraph.dot",
                            logger: MavenLogger
                          ): Unit = {

    // Stelle sicher, dass das Verzeichnis existiert
    if (!outputDirectory.exists()) {
      outputDirectory.mkdirs()
    }

    // Erstelle Pfad zur Output-Datei
    val outputFile = new File(outputDirectory, outputFileName)
    val writer = new FileWriter(outputFile)

    try {
      writer.write("digraph CallGraph {\n")
      writer.write("  rankdir=LR;\n") // Links-Rechts Layout
      writer.write("  node [shape=box, style=filled, fillcolor=lightblue];\n")

      // Alle Knoten und Kanten exportieren
      var i = 0
      breakable {
        callGraph.reachableMethods.foreach { method =>
          if (i > 120) break
          val methodLabel = s"${method.method.declaringClassType.simpleName}.${method.method.name}"
          i += 1
          callGraph.calleesOf(method.method).foreach { case (_, callees) =>
            callees.foreach { callee =>
              val calleeLabel = s"${callee.method.declaringClassType.simpleName}.${callee.method.name}"
              writer.write(s"  \"$methodLabel\" -> \"$calleeLabel\";\n")
            }
          }
        }
      }

      writer.write("}\n")

      logger.info(s"DOT-Datei geschrieben: ${outputFile.getAbsolutePath}")

    } finally {
      writer.close()
    }
  }
}
