import com.typesafe.config.ConfigValueFactory
import org.opalj.br.BaseConfig
import org.opalj.br.analyses.Project
import org.opalj.log.{Error, GlobalLogContext, LogContext, LogMessage, OPALLogger}
import org.opalj.tac.cg._

import java.io.File
import java.net.URL
import scala.collection.mutable.ListBuffer

// file=commons-collections4-4.5.0-M2.jar il=true cg=XTA dp=commons-lang3-3.14.0.jar
object task4_2 {

  /** Stores the path to the project. */
  private var projectFile: Array[File] = _
  /** Stores the path to the library. */
  private var libraryFile: Array[File] = _
  /** Indicates whether the project is a library. */
  private var projectIsLibrary: Boolean = _
  /** Stores the chosen call graph algorithm. */
  private var callGraphAlgorithm: String = _
  /** File Path for output and input for this task */
  private val path: String = "src/main/scala/4_2/"

  /** Main-Method
   * @param args User input */
  def main(args: Array[String]): Unit = {
    // user input
    readCommands(args)

    // project
    val project = loadProject
    //printProjectDetails(project) // only for tests

    // libraryProject
    val libraryProjectObjects = loadLibraryObjects
    //printLibraryObjects(libraryProject) // only for tests

    // prints class Files to test
    //printClassFiles() // only for tests

    // callGraph
    val callGraph = getCallGraph(project)
    //printCallGraphMethods(callGraph) // only for test
    val containsUsedObjects = calculateUsedMethodsFromLibrary(callGraph, libraryProjectObjects)

    // creates a new class for every library class used
    val creator = new CreateClasses()
    containsUsedObjects.foreach{ cf =>
      if(cf.nonEmpty){
        creator.createClass(cf)
      }
    }
  }

  /** Read user input
   *
   * @param args Given input from run-console. */
  private def readCommands(args: Array[String]): Unit = {
    args.foreach{ arg =>
      if(arg.contains("file")){
        projectFile = Array(new java.io.File(s"${path}Projekt/${arg.split('=')(1)}"))
      }
      else if(arg.contains("il")){
        projectIsLibrary = arg.split('=')(1).toBoolean
      }
      else if(arg.contains("cg")){
        callGraphAlgorithm = s"${arg.split('=')(1)}"
      }
      else if(arg.contains("dp")){
        libraryFile = Array(new java.io.File(s"${path}Library/${arg.split('=')(1)}"))
      }
    }
    if(!projectFile.exists(_.exists()) && !libraryFile.exists(_.exists())){
      throw new IllegalStateException()
    }
  }

  /** Creates project. */
  private def loadProject: Project[URL] = {
    // Initial configuration
    var config = BaseConfig

    // Adjustment of the configuration according to the type of the software system.
    if (projectIsLibrary) {
      // set entry point of library
      config = config.withValue(
        "org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis",
        ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryEntryPointsFinder")
      ).withValue(
        "org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis",
        ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder")
      )
    }
    OPALLogger.updateLogger(GlobalLogContext, new MyLogger)
    Project ( projectFile , libraryFile, GlobalLogContext, config)
  }

  /**
   * Creates a list for every class which contains a list of all methods. Stored as [[ComparableObject]]s.
   * @return Lists of a lists of [[ComparableObject]]s.
   */
  private def loadLibraryObjects: Array[Array[ComparableObject]] = {
    var config = BaseConfig
    config = config.withValue(
      "org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis",
      ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryEntryPointsFinder")
    ).withValue(
      "org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis",
      ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder")
    )
    OPALLogger.updateLogger(GlobalLogContext, new MyLogger)
    val project = Project(libraryFile.head, GlobalLogContext, config)

    val outputList: Array[Array[ComparableObject]] = project.allProjectClassFiles.map { cf =>
      Array(cf.methods.map { method =>
        new ComparableObject(cf.thisType, method.name, method.descriptor, cf, method)
      }: _*)
    }.toArray

    outputList
  }

  /** creates callGraph. */
  private def getCallGraph(project: Project[URL]): CallGraph = {
    callGraphAlgorithm match{
      case "CHA" => project.get( CHACallGraphKey )
      case "RTA" => project.get( RTACallGraphKey )
      case "XTA" => project.get( XTACallGraphKey )
      // not working
      case "CFA" => project.get( CFA_1_1_CallGraphKey )
      case _ => throw new IllegalArgumentException(s"No valid algorithm; given: $callGraphAlgorithm")
    }
  }

  /**
   * Calculates all used methods of the given library.
   * @param callGraph The created callGraph.
   * @param libraryProjectObjects All [[ComparableObject]] for the library.
   * @return Used methods of the library.
   */
  private def calculateUsedMethodsFromLibrary(callGraph: CallGraph, libraryProjectObjects: Array[Array[ComparableObject]]): Array[ListBuffer[ComparableObject]] = {
    val output: Array[ListBuffer[ComparableObject]] =
      callGraph.reachableMethods().foldLeft(Array.fill(libraryProjectObjects.length)(ListBuffer.empty[ComparableObject])) { (acc, usedObject) =>
        // Gehe durch alle Indizes von `libraryProjectObjects`
        libraryProjectObjects.indices.foreach { i =>
          // Überprüfe, ob `usedObject.method` in `libraryProjectObjects(i)` enthalten ist
          libraryProjectObjects(i).find(ele => ele.equals(usedObject.method)) match {
            case Some(ele) =>
              acc(i) += new ComparableObject(usedObject.method.declaringClassType, usedObject.method.name, usedObject.method.descriptor, ele.storeClassFile, ele.storeMethod)
            case None =>
            // Nichts tun, falls kein Element gefunden wird
          }
        }
        acc
      }
    output
  }

  /** Stops print into console when project is created. */
  private class MyLogger extends OPALLogger {
    override def log(message: LogMessage)(implicit ctx: LogContext): Unit = {
      message.level match {
        /*
        case Warn =>
          println(s"[WARN] ${message.message}")
         */
        case Error =>
          println(s"[ERR] ${message.message}")
        case _ =>
      }
    }
  }

  /**
   * Prints all fields and methods of the given project.
   * @param project The project you want to print.
   */
  private def printProjectDetails(project: Project[URL]): Unit = {
    println("Project fields:")
    project.allFields.foreach(println(_))
    println("Project methods:")
    project.allMethods.foreach(println(_))
    println()
  }

  /**
   * Prints all fields and methods of the given callGraph.
   * @param callGraph The created callGraph
   */
  private def printCallGraphMethods(callGraph: CallGraph): Unit = {
    println("CallGraph methods:")
    callGraph.reachableMethods().foreach{ ele =>
      println(ele.method.declaringClassType + " " + ele.method.name + " " + ele.method.descriptor)
    }
    println()
  }

  /** Prints a list of lists */
  private def printLibraryObjects(list: Array[Array[ComparableObject]]): Unit = {
    list.foreach{ cf =>
      cf.foreach { obj =>
        println(s"$obj\n")
      }
    }
  }

  /**
   * Prints all class files which were created/ are in the 4_2/output folder.
   */
  private def printClassFiles(): Unit = {
    println("Output Files:")
    val files: Array[java.io.File] = getOutputClasses
    files.foreach{ file =>
      val project_test = Project(file) // depends on where you save Test.class
      printProjectDetails(project_test) // only for tests
    }
  }

  /**
   * Only for tests - gets all created class file paths (from output folder).
   * @return Paths to classes.
   */
  private def getOutputClasses = {
    val pathToDirectory = s"${path}Output/"
    val directory = new File(pathToDirectory)

    val arrayOfFiles = ListBuffer[File]()

    // check if directory exist
    if (directory.exists && directory.isDirectory) {
      val filesAndDirs = directory.listFiles()
      filesAndDirs.foreach { file =>
        arrayOfFiles += new java.io.File(pathToDirectory + file.getName)
      }
    } else {
      throw new IllegalArgumentException(s"${directory.getPath} ist kein gültiges Verzeichnis")
    }
    arrayOfFiles.toArray
  }
}
