package Task3
import Util.ComparableObject
import com.typesafe.config.ConfigValueFactory
import org.opalj.br.{BaseConfig, ClassFile, FieldType, Method, ObjectType}
import org.opalj.br.analyses.Project
import org.opalj.br.instructions.{GETFIELD, GETSTATIC, PUTFIELD, PUTSTATIC}
import org.opalj.br.reader.Java17Framework
import org.opalj.log.{Error, GlobalLogContext, LogContext, LogMessage, OPALLogger}
import org.opalj.tac.cg.{CFA_1_1_CallGraphKey, CHACallGraphKey, CallGraph, RTACallGraphKey, XTACallGraphKey}

import java.io.{File, PrintWriter}
import java.net.URL
import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode

case class LibraryFiles(classFile: ClassFile, method: Method)
case class CustomField(declaringClass: ObjectType, name: String, fieldType: FieldType)

class Analyse {

  /** Holds the path to the project. */
  private var projectFiles: Array[File] = _

  /** Holds the paths to the dependent libraries. */
  private var libraryFiles: Array[File] = _

  /** Holds the algorithm used for the call graph. */
  private var callGraphAlgorithm: String = _

  /** Should be true if the project itself is a library. */
  private var projectIsLibrary: Boolean = _

  /** Used for api calculation */
  private val fields: ListBuffer[ListBuffer[CustomField]] = ListBuffer[ListBuffer[CustomField]]()

  private var targetPath: String = _

  /**
   * Actual implementation
   */
  def run(filePath: File, libraryPath: File, isLibrary: Boolean, key: String, targetPath: String): Unit = {
    // process the input
    this.projectFiles = Array(filePath)
    this.libraryFiles = getLibrary(libraryPath)
    this.projectIsLibrary = isLibrary
    this.callGraphAlgorithm = key
    this.targetPath = targetPath

    // count used methods of libraries
    val project = loadProject

    // creation of callGraph
    val startTime = System.currentTimeMillis()
    val callGraph = getCallGraph(project)
    val durationAfterCallGraph = System.currentTimeMillis() - startTime

    // gets for every library all methods
    val libraryMethods = getLibraryMethods(project)
    // only in the project
    // only public methods
    val publicLibraryMethods = getPublicLibraryMethods(libraryMethods, callGraph, durationAfterCallGraph)
    // api
    val libraryAPI = getApi(project, callGraph, libraryMethods, durationAfterCallGraph)

    // output
    printResult(publicLibraryMethods+libraryAPI)
  }

  /**
   * Loads the Project. Uses [[projectFiles]], [[libraryFiles]] and [[projectIsLibrary]].
   *
   * @return The loaded project.
   */
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
    val logg: OPALLogger = new MyLogger
    OPALLogger.updateLogger(GlobalLogContext, logg)
    val libraryClassFiles = Java17Framework.AllClassFiles ( libraryFiles )
    val projectClassFiles = Java17Framework.AllClassFiles ( projectFiles )
    Project ( projectClassFiles , libraryClassFiles ,
      libraryClassFilesAreInterfacesOnly = false ,
      virtualClassFiles = Iterable.empty)( config, logg )
  }

  /**
   * Calculates all methods for each library.
   * @param project The Project
   * @return Methods of each library
   */
  private def getLibraryMethods(project: Project[URL]): ListBuffer[ListBuffer[LibraryFiles]] = {
    val list: ListBuffer[ListBuffer[LibraryFiles]] = ListBuffer[ListBuffer[LibraryFiles]]()

    libraryFiles.foreach{ library =>
      val logg: OPALLogger = new MyLogger
      OPALLogger.updateLogger(GlobalLogContext, logg)
      val project = Project(library)
      val otherList: ListBuffer[LibraryFiles] = ListBuffer[LibraryFiles]()
      val otherFields: ListBuffer[CustomField] = ListBuffer[CustomField]()

      project.allProjectClassFiles.foreach{ classFile =>
        // methods
        classFile.methods.foreach{ method =>
          otherList += LibraryFiles(classFile, method)
        }
        // fields
        classFile.fields.foreach{ field =>
          otherFields += CustomField(classFile.thisType, field.name, field.fieldType)
        }
      }
      list += otherList
      fields += otherFields
    }
    list
  }

  /**
   * Calculates all public library methods first,
   * and then creates an output string showing the proportion of used methods.
   * @return A string representing the proportion of used public methods.
   */
  private def getPublicLibraryMethods(libraryMethods: ListBuffer[ListBuffer[LibraryFiles]], callGraph: CallGraph, durationCallGraph: Long): String = {
    // only public methods
    val list: ListBuffer[ListBuffer[ComparableObject]] =  ListBuffer[ListBuffer[ComparableObject]]()
    libraryMethods.foreach{ library =>
      list += library.filter(_.method.isPublic).map{ e =>
        new ComparableObject(e.method)
      }
    }

    val startTime = System.currentTimeMillis()
    val counterAll: ListBuffer[Int] = getCounter(callGraph, list, list.size)
    val duration = System.currentTimeMillis() - startTime
    val counterMax = ListBuffer[Int]()
    list.foreach{ size =>
      counterMax += size.size
    }
    s"$callGraphAlgorithm\nPublic\n${createOutput(counterMax, counterAll, durationCallGraph+duration)}\n"
  }

  /**
   * Calculates used API. Methods and Fields.
   * @param project The project.
   * @param callGraph The created callGraph.
   * @param libraryMethods All library methods.
   * @param durationCallGraph Duration needed to create callGraph.
   * @return
   */
  private def getApi(project: Project[URL], callGraph: CallGraph, libraryMethods: ListBuffer[ListBuffer[LibraryFiles]], durationCallGraph: Long): String = {
    // only public methods
    val list: ListBuffer[ListBuffer[ComparableObject]] =  ListBuffer[ListBuffer[ComparableObject]]()
    libraryMethods.foreach{ library =>
      list += library.map{ e =>
        new ComparableObject(e.method)
      }
    }

    //// only imported
    val startTime = System.currentTimeMillis()
    // used methods
    val counterAPI: ListBuffer[Int] = getCounter(callGraph, list, list.size)
    // used fields
    val counterFields = getCounterFields(project, callGraph, list)
    val counterResult = ListBuffer[Int]()
    for(i <- counterAPI.indices){
      counterResult += counterAPI(i) + counterFields(i)
    }
    val duration= System.currentTimeMillis() - startTime
    val counterMax = ListBuffer[Int]()
    for(i <- list.indices){
      counterMax += list(i).size + fields(i).size
    }
    s"\nAPI\n${createOutput(counterMax, counterResult, durationCallGraph+duration)}"
  }

  /**
   * Calculates how many fields from the libraries are used.
   * @param project The project
   * @param callGraph The callgraph created.
   * @param libraryMethods All library methods.
   * @return
   */
  private def getCounterFields(project: Project[URL], callGraph: CallGraph, libraryMethods: ListBuffer[ListBuffer[ComparableObject]]): ListBuffer[Int] = {
    val reachMethod: List[ComparableObject] = callGraph.reachableMethods().iterator.toList.map(elem => new ComparableObject(elem.method))
    val usedFields = ListBuffer[CustomField]()
    project.allLibraryClassFiles.filter(_.isPublic).foreach{ classFile =>
      classFile.methodsWithBody.foreach{ method =>
        if(reachMethod.exists(obj => obj.checkClass == classFile.thisType && obj.checkMethodName == method.name && obj.checkMethodDescriptor == method.descriptor)){
          method.body.get.instructions.foreach{ instr =>
            if (instr != null) {
              instr match {
                case GETFIELD(owner, fieldName, fieldType) =>
                  usedFields += CustomField(owner, fieldName, fieldType)
                case PUTFIELD(owner, fieldName, fieldType) =>
                  usedFields += CustomField(owner, fieldName, fieldType)
                // Weitere relevante Instruktionen, z.B. für statische Felder (GETSTATIC, PUTSTATIC)
                case GETSTATIC(owner, fieldName, fieldType) =>
                  usedFields += CustomField(owner, fieldName, fieldType)
                case PUTSTATIC(owner, fieldName, fieldType) =>
                  usedFields += CustomField(owner, fieldName, fieldType)
                // Weitere Anweisungen oder Variablenzugriffe können hier ebenfalls berücksichtigt werden
                case _ =>
              }
            }
          }
        }
      }
    }
    val counter = ListBuffer.fill(fields.size)(0)
    for(i <- fields.indices){
      fields(i).foreach{ field =>
        if(usedFields.contains(field)){
          counter(i) += 1
        }
      }
    }
    counter
  }

  /**
   * Creates a call graph using the algorithm specified in [[callGraphAlgorithm]].
   *
   * @param project The project from which the call graph is created.
   * @return The created call graph.
   * @throws IllegalArgumentException If set [[callGraphAlgorithm]] is not valid.
   */
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
   * Counts the number of methods used from each library.
   *
   * @param callGraph      The call graph to check if a method can be called.
   * @param libraryMethods The methods of the libraries.
   * @param size           The number of libraries provided.
   * @return The number of used methods for each library.
   */
  private def getCounter(callGraph: CallGraph, libraryMethods: ListBuffer[ListBuffer[ComparableObject]], size: Int): ListBuffer[Int] = {
    val counter = ListBuffer.fill(size)(0)

    val test = callGraph.reachableMethods()
    val testAr = test.toArray
    for (i <- testAr.indices) {
      val method = testAr(i)
      //println(s"${i+1} / ${testAr.length}")
      for (i <- libraryMethods.indices) {
        libraryMethods(i).foreach { library =>
          if (library.checkClass == method.method.declaringClassType && library.checkMethodName == method.method.name && library.checkMethodDescriptor == method.method.descriptor) {
            // has a caller - false for <clinit> for some reason
            if (callGraph.callersOf(method.method).iterator.toList.nonEmpty) {
              counter(i) += 1
              //println(s"${Util.ComparableObject(classFile.thisType, method.name, method.parameterTypes)}") - called method
            }
          }
        }
      }
    }
    counter
  }

  /**
   * Creates output message.
   * @param maxSize List of all max sizes calculated.
   * @param counter Amount every library was used.
   * @param duration Time needed for the analysis
   * @return Output String
   */
  private def createOutput(maxSize: ListBuffer[Int], counter: ListBuffer[Int], duration: Long): String = {
    var output = ""
    maxSize.indices.foreach{ ind =>
      output += s"Für ${libraryFiles(ind).getName}:  \nAngeboten: ${maxSize(ind)}, Genutzt: ${counter(ind)}"
      try{
        output += s", In Prozent: ${(BigDecimal(counter(ind)/maxSize(ind).toDouble)*100).setScale(2, RoundingMode.HALF_UP) }%\n"
      }catch{
        case _: Throwable =>
          output += s", In Prozent: 100%\n"
      }
    }
    output += s"Laufzeit: ${duration/1000.0} s"
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

  private def getLibrary(libraryPath: File): Array[File] = {
    val filesList = libraryPath.listFiles()
    filesList.filter(file => file.getPath.split('.').last == "jar")
  }

  /**
   * Prints the result of this analysis into a file with [[targetPath]] as its path.
   */
  private def printResult(output: String): Unit = {
    // Specify the file name
    val fileName = "Task3.txt"
    // Create a PrintWriter to write to the file
    val writer = new PrintWriter(targetPath + "/" + fileName)
    // Write content to the file
    writer.println(output)
    // Close the writer to release resources
    writer.close()
  }
}


