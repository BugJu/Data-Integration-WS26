file:///C:/Users/david/Documents/Uni/BA/Aufgabe2_3/Aufgabe2_3/src/main/scala/Aufgabe3/Analyse.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -org/opalj/br/reader/Java17Framework.AllClassFiles.
	 -org/opalj/br/reader/Java17Framework.AllClassFiles#
	 -org/opalj/br/reader/Java17Framework.AllClassFiles().
	 -Java17Framework.AllClassFiles.
	 -Java17Framework.AllClassFiles#
	 -Java17Framework.AllClassFiles().
	 -scala/Predef.Java17Framework.AllClassFiles.
	 -scala/Predef.Java17Framework.AllClassFiles#
	 -scala/Predef.Java17Framework.AllClassFiles().
offset: 4403
uri: file:///C:/Users/david/Documents/Uni/BA/Aufgabe2_3/Aufgabe2_3/src/main/scala/Aufgabe3/Analyse.scala
text:
```scala
package Aufgabe3

import com.typesafe.config.ConfigValueFactory
import org.opalj.br.{BaseConfig, ClassFile, FieldType, Method, ObjectType}
import org.opalj.br.analyses.Project
import org.opalj.br.instructions.{GETFIELD, GETSTATIC, PUTFIELD, PUTSTATIC}
import org.opalj.br.reader.Java17Framework
import org.opalj.tac.cg.{CFA_1_1_CallGraphKey, CHACallGraphKey, CallGraph, RTACallGraphKey, XTACallGraphKey}

import java.io.{File, FileWriter, PrintWriter}
import java.net.URL
import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode

case class LibraryFiles(classFile: ClassFile, method: Method)
case class CustomField(declaringClass: ObjectType, name: String, fieldType: FieldType)

// file=commons-collections4-4.5.0-M2.jar dp=commons-collection cg=CHA il=true
// file=pdfbox-app-3.0.3.jar dp=pdfbox cg=CHA il=false
// file=gson-2.11.0.jar dp=gson cg=CHA il=true
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
  private var fields: ListBuffer[ListBuffer[CustomField]] = ListBuffer[ListBuffer[CustomField]]()

  /**
   * Actual implementation
   * @param args Inputs from the console.
   */
  def run(args: Array[String]): Unit = {
    // process the input
    readInput(args)

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
    printer(s"${projectFiles.head.toString.replace(".jar", "")}.txt".replace("Aufgabe3\\Projekte", "Aufgabe3\\Output"), publicLibraryMethods+libraryAPI)
  }

  /**
   * Reads the input from `args` and assigns the values to the desired fields.
   * Additionally, it checks if valid paths are provided for the files.
   * - `file` => [[projectFiles]]
   * - `dp` => [[libraryFiles]]
   * - `cg` => [[callGraphAlgorithm]]
   * - `il` => [[projectIsLibrary]]
   *
   * @param args Input from the console.
   * @throws IllegalStateException If the project file or library file cannot be found.
   */
  private def readInput(args: Array[String]): Unit = {
    args.foreach{ arg =>
      if(arg.contains("file")){
        projectFiles = Array(new java.io.File(s"src//main//scala//Aufgabe3//Projekte//${arg.split('=')(1)}"))
      }
      else if(arg.contains("dp")){
        libraryFiles = new LoadLibraries().getClassesAndMethodsOfLibrary(s"${arg.split('=')(1)}")
      }
      else if(arg.contains("cg")){
        callGraphAlgorithm = s"${arg.split('=')(1)}"
      }
      else if(arg.contains("il")){
        projectIsLibrary = arg.split('=')(1).toBoolean
      }
    }
    if(!projectFiles.exists(_.exists()) || !libraryFiles.exists(_.exists())){
      throw new IllegalStateException()
    }
    fields = ListBuffer[ListBuffer[CustomField]]()
  }

  /**
   * Loads the Project. Uses [[projectFiles]], [[libraryFiles]] and [[projectIsLibrary]].
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
    val libraryClassFiles = Java17Framework.AllClassF@@iles ( libraryFiles )
    val projectClassFiles = Java17Framework.AllClassFiles ( projectFiles )

    Project ( projectClassFiles , libraryClassFiles ,
      libraryClassFilesAreInterfacesOnly = false ,
      virtualClassFiles = Iterable.empty)( config )
  }

  /**
   * Calculates all methods for each library.
   * @param project The Project
   * @return Methods of each library
   */
  private def getLibraryMethods(project: Project[URL]): ListBuffer[ListBuffer[LibraryFiles]] = {
    val list: ListBuffer[ListBuffer[LibraryFiles]] = ListBuffer[ListBuffer[LibraryFiles]]()

    libraryFiles.foreach{ library =>
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
        ComparableObject(e.classFile.id, e.method.name, e.method.parameterTypes )
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
        ComparableObject(e.classFile.id, e.method.name, e.method.parameterTypes)
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
    val reachMethod: List[ComparableObject] = callGraph.reachableMethods().iterator.toList.map(elem => ComparableObject(elem.method.declaringClassType.id, elem.method.name, elem.method.descriptor.parameterTypes))
    val usedFields = ListBuffer[CustomField]()
    project.allLibraryClassFiles.filter(_.isPublic).foreach{ classFile =>
      classFile.methodsWithBody.foreach{ method =>
        if(reachMethod.exists(obj => obj.checkClass == classFile.id && obj.checkMethodName == method.name && obj.checkMethodParameter == method.parameterTypes)){
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
          if (library.checkClass == method.method.declaringClassType.id && library.checkMethodName == method.method.name && library.checkMethodParameter == method.method.descriptor.parameterTypes) {
            // has a caller - false for <clinit> for some reason
            if (callGraph.callersOf(method.method).iterator.toList.nonEmpty) {
              counter(i) += 1
              //println(s"${ComparableObject(classFile.thisType, method.name, method.parameterTypes)}") - called method
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
      output += s"Für ${libraryFiles(ind).toString.replace("src\\main\\scala\\Aufgabe3\\Projekte\\","")}:  \nAngeboten: ${maxSize(ind)}, Genutzt: ${counter(ind)}"
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

  /**
   * Prints the result to a file.
   * @param filename The file to which the result is printed.
   * @param output The result to be printed.
   * @param append If true, does not overwrite the content if the file is not empty, but appends the result.
   */
  private def printer(filename: String, output: String, append: Boolean = true): Unit = {
    val writer = new PrintWriter(new FileWriter(filename, append)) // init writer
    try {
      writer.println(output) // write into file
      writer.println(s"\n------------------------------------------------------------------\n")
    } finally {
      writer.close() // close writer
    }
  }
}



```


#### Short summary: 

empty definition using pc, found symbol in pc: 