package uni.dj

import com.typesafe.config.ConfigValueFactory
import org.opalj.br.{BaseConfig, Method}
import org.opalj.br.analyses.Project
import org.opalj.br.reader.Java17Framework
import org.opalj.tac.cg.{CHACallGraphKey, RTACallGraphKey, XTACallGraphKey}
import org.opalj.tac.cg.CallGraph

import java.io.File
import java.net.URL
import java.nio.file.{FileSystems, Files}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.IteratorHasAsScala

/*
    Static object for finding unreachable dependencies using the OPAL framework.
 */
object OpalFindUnreachableDependencies {

  /*
      Analyzes the project and its libraries to identify unreachable dependencies.
   */
  def findUnreachableDependencies(
                                   classFileDirectory: String,
                                   logger: MavenLogger,
                                   libraryFiles: ListBuffer[File],
                                   outputDir: File,
                                   outputFileName: String
                                 ): Unit = {
    logger.info("Loading project...")
    val libraryFilesArray: Array[File] = libraryFiles.toArray[File]
    val project = loadProject(classFileDirectory, logger, libraryFilesArray)
    logger.info(s"Library classes: ${project.libraryClassFilesCount}")
    //val cg = getCallGraph(project, logger, 3)
  }

  /*
      Loads the OPAL project from project class files and library JARs.
      @returns The initialized OPAL Project instance.
   */
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

    logger.info(s"Project classes loaded: ${projectClassFiles.size}")
    logger.info(s"Library classes loaded: ${libraryClassFiles.size}")

    val config = BaseConfig
      .withValue(
        "org.opalj.br.analyses.cg.InitialEntryPointsKey.entryPointFinder",
        ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.AllEntryPointsFinder")
      )
      .withValue(
        "org.opalj.br.analyses.cg.InitialEntryPointsKey.AllEntryPointsFinder.projectMethodsOnly",
        ConfigValueFactory.fromAnyRef(false)
      )
    Project(
      projectClassFiles,
      libraryClassFiles,
      libraryClassFilesAreInterfacesOnly = false,
      virtualClassFiles = Iterable.empty
    )(config)
  }

  /*
      Constructs a call graph for the project using the specified algorithm (CHA, RTA, or XTA).
      @returns The constructed CallGraph instance.
   */
  private def getCallGraph(project: Project[URL], logger: MavenLogger, typ: Int): CallGraph = {
    try {
      typ match {
        case 1 =>
          logger.info("Using CHA call graph...")
          project.get(CHACallGraphKey)
        case 2 =>
          logger.info("Using RTA call graph...")
          project.get(RTACallGraphKey)
        case 3 =>
          logger.info("Using XTA call graph...")
          project.get(XTACallGraphKey)
        case _ =>
          logger.error(s"Invalid call graph type: $typ")
          throw new IllegalArgumentException(s"Call graph type must be 1-3, got $typ")
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to construct call graph: ${e.getMessage}")
        throw e
    }
  }

  /*
      Placeholder for finding dependency collisions (not yet implemented).
   */
  private def findDependencieCollision(): Unit = {

  }
}

/**
 * case class JarClassMapping(jarName: String, classNames: Set[String])
 *
 * case class MethodSignature(fqn: String, name: String, descriptor: String) {
 * override def toString: String = s"$fqn#$name$descriptor"
 * }
 * }
 *
 * Normalisiert FQNs zu konsistenter Schreibweise (Slashes statt Punkte)
 * private def normalizeFqn(fqn: String): String = {
 * fqn.replace('.', '/')
 * }
 *
 * Erstellt eine eindeutige Signatur für eine Methode
 *
 * private def methodSignature(method: Method): MethodSignature = {
 * val fqn = normalizeFqn(method.classFile.thisType.fqn)
 * val name = method.name
 * val descriptor = method.descriptor.toJVMDescriptor
 * MethodSignature(fqn, name, descriptor)
 * }
 *
 * private def analyzeJarCoverage(
 * callGraph: CallGraph,
 * logger: MavenLogger,
 * project: Project[URL],
 * jarClassMappings: ListBuffer[JarClassMapping]
 * ): Unit = {
 *
 * logger.info("=== JAR COVERAGE ANALYSIS ===\n")
 *
 * // 1. Sammle reachable Methods mittels Methoden-Signaturen (nicht Objekt-Referenzen)
 * val reachableMethodSignatures: Set[MethodSignature] = callGraph.reachableMethods.flatMap { methodInfo =>
 * val declaredMethod = methodInfo.method
 * if (declaredMethod.hasSingleDefinedMethod) {
 * Set(methodSignature(declaredMethod.definedMethod))
 * } else if (declaredMethod.hasMultipleDefinedMethods) {
 * declaredMethod.definedMethods.map(methodSignature).toSet
 * } else {
 * Set.empty
 * }
 * }.toSet
 *
 * logger.info(s"Total reachable method signatures: ${reachableMethodSignatures.size}")
 * logger.info(s"Total library class files loaded: ${project.allLibraryClassFiles.size}")
 * logger.info(s"Total project class files loaded: ${project.allProjectClassFiles.size}")
 *
 * // Debug: Zeige Sample FQNs aus Projekt und Library
 * val sampleLibraryFQNs = project.allLibraryClassFiles.take(5).map(_.thisType.fqn).toSeq
 * logger.info(s"Sample library FQNs (${sampleLibraryFQNs.size}): ${sampleLibraryFQNs.mkString(", ")}")
 *
 * val firstJarMapping = jarClassMappings.headOption
 * firstJarMapping.foreach { jar =>
 * val sampleJarClasses = jar.classNames.take(5).toSeq
 * logger.info(s"Sample classes from first JAR ${jar.jarName} (${sampleJarClasses.size}): ${sampleJarClasses.mkString(", ")}")
 * }
 *
 * // 2. Für jedes JAR: Analysiere Coverage mit normalisiertem Klassenvergleich
 * val jarUsageStats: Seq[JarUsageStats] = jarClassMappings.toSeq.map { jarMapping =>
 * val classesInJar = jarMapping.classNames
 *
 * // Normalisiere JAR-Klassennamen zur Vergleichbarkeit
 * val normalizedJarClasses = classesInJar.map(normalizeFqn)
 *
 * // Debug: Zeige erste Klassennamen aus dem JAR (nur für erste 3 JARs)
 * if (classesInJar.nonEmpty && jarClassMappings.indexOf(jarMapping) < 3) {
 * logger.info(s"Sample class names from ${jarMapping.jarName}: ${classesInJar.take(5).mkString(", ")}")
 * }
 *
 * // Filtere alle Class Files (Library + Project), die zu diesem JAR gehören
 * // Nutze normalisierte FQNs für Vergleich
 * val allRelevantClassFiles = (project.allLibraryClassFiles ++ project.allProjectClassFiles)
 * .filter(cf => normalizedJarClasses.contains(normalizeFqn(cf.thisType.fqn)))
 *
 * // Zähle alle Methoden im JAR mit Body (implementierte Methoden)
 * val totalMethodsInJar = allRelevantClassFiles
 * .flatMap(_.methods)
 * .count(_.body.isDefined)
 *
 * // Zähle reachable Methoden durch Signatur-Vergleich (nicht Objekt-Referenz)
 * val reachableMethodsCount = allRelevantClassFiles
 * .flatMap(_.methods)
 * .count(m => m.body.isDefined && reachableMethodSignatures.contains(methodSignature(m)))
 *
 * val reachableClassesCount = allRelevantClassFiles.size
 *
 * // Debug nur für erste 3 JARs
 * if (jarClassMappings.indexOf(jarMapping) < 3) {
 * logger.info(s"JAR ${jarMapping.jarName}: ${allRelevantClassFiles.size} matched classes, ${totalMethodsInJar} methods, ${reachableMethodsCount} reachable")
 * }
 *
 * JarUsageStats(
 * jarName = jarMapping.jarName,
 * totalClasses = classesInJar.size,
 * reachableClasses = reachableClassesCount,
 * totalMethods = totalMethodsInJar,
 * reachableMethods = reachableMethodsCount
 * )
 * }.sortBy(-_.reachableMethods)
 *
 * // 3. Report
 * logger.info(s"Total JARs analyzed: ${jarUsageStats.length}\n")
 *
 * // Completely unused
 * val completelyUnused = jarUsageStats.filter(_.reachableMethods == 0)
 * logger.info(s"=== COMPLETELY UNUSED JARS (${completelyUnused.length}) ===")
 * if (completelyUnused.nonEmpty) {
 * completelyUnused.foreach { stat =>
 * logger.info(f"  [${stat.jarName}%-60s] 0/${stat.totalMethods} methods, 0/${stat.totalClasses} classes")
 * }
 * } else {
 * logger.info("  (none)")
 * }
 *
 * // Partially used
 * val partiallyUsed = jarUsageStats.filter(s => s.reachableMethods > 0 && s.reachableMethods < s.totalMethods)
 * logger.info(f"\n=== PARTIALLY USED JARS (${partiallyUsed.length}) ===")
 * logger.info("(Top 15 by usage)")
 * partiallyUsed.take(15).foreach { stat =>
 * val coverage = (stat.reachableMethods * 100.0) / stat.totalMethods
 * logger.info(f"  [${stat.jarName}%-60s] ${stat.reachableMethods}/${stat.totalMethods} methods (${coverage}%3.1f%%), ${stat.reachableClasses}/${stat.totalClasses} classes")
 * }
 *
 * // Fully used
 * val fullyUsed = jarUsageStats.filter(s => s.reachableMethods == s.totalMethods && s.totalMethods > 0)
 * logger.info(f"\n=== FULLY USED JARS (${fullyUsed.length}) ===")
 * if (fullyUsed.nonEmpty) {
 * fullyUsed.take(10).foreach { stat =>
 * logger.info(f"  [${stat.jarName}%-60s] ${stat.reachableMethods}/${stat.totalMethods} methods")
 * }
 * }
 *
 * // Statistics
 * logger.info(s"\n=== SUMMARY ===")
 * logger.info(s"Total JARs: ${jarUsageStats.length}")
 * logger.info(s"Completely unused: ${completelyUnused.length} (${if (jarUsageStats.nonEmpty) (completelyUnused.length * 100.0) / jarUsageStats.length else 0}%.1f%%)")
 * logger.info(s"Partially used: ${partiallyUsed.length}")
 * logger.info(s"Fully used: ${fullyUsed.length}")
 *
 * val totalMethods = jarUsageStats.map(_.totalMethods).sum
 * val totalReachable = jarUsageStats.map(_.reachableMethods).sum
 * logger.info(s"Total methods in all JARs: $totalMethods")
 * logger.info(s"Total reachable methods: $totalReachable (${if (totalMethods > 0) (totalReachable * 100.0) / totalMethods else 0}%.1f%%)")
 * }
 * }
 * case class JarUsageStats(
 * jarName: String,
 * totalClasses: Int,
 * reachableClasses: Int,
 * totalMethods: Int,
 * reachableMethods: Int
 * )
 */