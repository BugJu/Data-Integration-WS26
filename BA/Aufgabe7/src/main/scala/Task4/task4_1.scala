package Task4

import Util.ComparableObject
import com.typesafe.config.ConfigValueFactory
import org.opalj.br.BaseConfig
import org.opalj.br.analyses.Project
import org.opalj.log.{Error, GlobalLogContext, LogContext, LogMessage, OPALLogger}
import org.opalj.tac.cg._

import java.io.{File, PrintWriter}
import java.net.URL
import scala.collection.mutable.ListBuffer

// sbt clean coverage test coverageReport
// file=untitled.jar il=false cg=XTA
object task4_1 {

  /** Stores the path to the project. */
  private var projectFile: File = _
  /** Indicates whether the project is a library. */
  private var projectIsLibrary: Boolean = _
  /** Stores the chosen call graph algorithm. */
  private var callGraphAlgorithm: String = _
  /** Stores the markers for all methods being searched for, as [[ComparableObject]]. Used as dummies. */
  private var searchFor: ListBuffer[ComparableObject] = _
  /** Stores the markers for all methods found that match a searched method, as [[ComparableObject]]. */
  private val foundCallees: ListBuffer[ComparableObject] = ListBuffer[ComparableObject]()

  private var targetPath: String = _

  /** Main-Method */
  def main(projectFile: File, projectIsLibrary: Boolean, callGraphAlgorithm: String, searchFor: ListBuffer[ComparableObject], targetPath: String): Unit = {
    // user input
    this.projectFile = projectFile
    this.projectIsLibrary = projectIsLibrary
    this.callGraphAlgorithm = callGraphAlgorithm
    this.searchFor = searchFor
    this.targetPath = targetPath

    // project
    val project = loadProject
    //printProjectDetails(project) // only for tests

    ////////// only for tests //////////
    //printClassFiles()
    ////////////////////////////////////

    // callGraph
    val callGraph = getCallGraph(project)
    println("Test")
    //printCallGraphMethods(callGraph) // only for test
    findCallers(callGraph)

    // create new classes for classes that use searched methods
    val creator = new ModifyClasses(foundCallees, searchFor, projectFile.getPath, targetPath)
    // checks if class is one of the found -> if yes creates a new class
    var output = ""
    project.allProjectClassFiles.filter(classFile => foundCallees.exists(ele => classFile.thisType == ele.checkClass)).foreach{ classFile =>
      output += creator.modifyClass(classFile)
    }
    printResult("Modified classes:\n" + output)
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
    Project ( projectFile , GlobalLogContext, config)
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

  /** Search for methods that call at least one of the searched methods.
   * @param callGraph created Callgraph */
  private def findCallers(callGraph: CallGraph): Unit = {
    callGraph.reachableMethods().filter(m => searchFor.exists(ele => ele.equals(m.method))).foreach{ method =>
      //println(s"Called: $method") // only for tests
      callGraph.callersOf(method.method).iterator.toList.foreach{ caller =>
        //println(s"Caller: $caller") // only for tests
        // doesn't already exist -> add to foundClasses
        if(!foundCallees.exists(ele => ele.equals(caller._1))){
          foundCallees += new ComparableObject(caller._1)
        }
      }
      //println() // only for tests
    }
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

  /**
   * Prints all class files which were created/ are in the 4_1/Output folder.
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
    val pathToDirectory = s"Output/"
    val directory = new File(pathToDirectory)

    val arrayOfFiles = ListBuffer[File]()
    arrayOfFiles.foreach(println(_))
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

  /**
   * Prints the result of this analysis into a file with [[targetPath]] as its path.
   */
  private def printResult(output: String): Unit = {
    // Specify the file name
    val fileName = "Task4_1.txt"
    // Create a PrintWriter to write to the file
    val writer = new PrintWriter(targetPath + "/" + fileName)
    // Write content to the file
    writer.println(output)
    // Close the writer to release resources
    writer.close()
  }
}
