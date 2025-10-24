package Task2

import org.opalj.br.ObjectType
import org.opalj.br.analyses.Project
import org.opalj.br.instructions.MethodInvocationInstruction
import org.opalj.log.{Error, GlobalLogContext, LogContext, LogMessage, OPALLogger}

import java.io.{File, PrintWriter}
import java.net.URL

object Task2 {

  private var filePath: File = _

  private var searchedMethod: List[String] = _

  private var targetPath: String = _

  /**
   * Used to set the limits.
   * @param methods The given methods that are searched.
   */
  def init(methods: List[String], filePath: String, targetPath: String): Unit = {
    searchedMethod = methods
    this.filePath = getFile(new java.io.File(filePath))
    checkInput(searchedMethod)
    this.targetPath = targetPath
  }

  def analyse(): Unit = {
    // create opal project
    val project = createProject(filePath)

    // init. variable
    var wasFoundInClass: Boolean = false
    var foundMethod: String = ""
    var output: String = ""
    var startOutput: String = ""

    output = s"\nGiven FilePath: $filePath, Methods: ${searchedMethod.toString()}\n"

    project.allProjectClassFiles.foreach { classFile =>
      // only used if we find one of the searched methods in this class
      startOutput += s"The class '${classFile.fqn}' contains the searched methods in the following methods:\n"
      classFile.methodsWithBody.foreach { method =>
        // ignore constructor
        if (method.body.isDefined) {
          val body = method.body.get
          body.instructions.foreach { instr =>
            if (instr != null) {
              //println(s"Instruction: ${instr.toString()}")
              instr match {
                // check if given instr. call/ creates one of the searched methods
                // static methods
                case invocation: MethodInvocationInstruction =>
                  invocation.declaringClass match {
                    case objectType: ObjectType =>
                      if ( {foundMethod = methodCalled(objectType.fqn.split("/").last, invocation.name, searchedMethod); foundMethod
                      } != "") {
                        wasFoundInClass = true
                        startOutput += s"   The Method '${method.name}' invokes $foundMethod\n"
                      }
                    case _ =>
                  }
                case _ => //
              }
            }
          }
        }
      }
      // if we found least one of the searched methods
      if( wasFoundInClass ){
        output += startOutput
        wasFoundInClass = false
      }
      startOutput = ""
    }
    printResult(output)
  }

  /**
   * Reads the Input from args.
   *
   * @param args Input from console.
   * @return ListBuffer with every parameter from the console.
   */
  private def checkInput(args: List[String]): Unit = {
    args.foreach{ arg =>
      // valid searches should consist of {class name.method name}
      if(arg.split('.').length != 2 ){
        throw new IllegalArgumentException(s"valid searches should consist of {class name.method name}! (file: {file name.data type}) - Found: $arg")
      }
    }
    // no arguments or file? given
    if(args.size < 1){
      throw new IllegalArgumentException("no arguments given!")
    }
  }

  /**
   * Used to identify if instruction is calling one of the searched methods.
   *
   * @param className Class which calls the method
   * @param methodName Name of the called method.
   * @param searchedMethods A list of the searchedMethods
   * @return Boolean type; Information for calling method if instruction was actually using searched method.
   */
  private def methodCalled(className : String, methodName: String, searchedMethods : List[String]): String = {
    // check if one of the searched methods is called in the instruction
    searchedMethods.foreach{ searchMethod =>
      // searching class name: e.g. System
      val searchClassName = searchMethod.split('.')(0)
      // searching method name: e.g. setSecurityManager
      val searchMethodName = searchMethod.split('.')(1)
      if(className==searchClassName &&  methodName==searchMethodName){
        return s"$className.$methodName"
      }
    }
    ""
  }

  /**
   * Creates the project.
   *
   * @param file Path to the file.
   * @return Created Project
   */
  private def createProject(file: java.io.File): Project[URL] = {
    // File exist?
    if (!file.exists()) {
      throw new IllegalStateException("The file does not exist. Please check the set path.")
    }
    // project creation
    val logg: OPALLogger = new MyLogger
    OPALLogger.updateLogger(GlobalLogContext, logg)
    val project = Project(file)

    // valid file?
    if (project.allProjectClassFiles.isEmpty) {
      throw new IllegalStateException("The file at the given path is not a valid file.")
    }
    // return
    project
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

  private def getFile(folder: File): File = {
    val filesList = folder.listFiles()
    val projectList = filesList.filter(file => file.getPath.split('.').last == "jar")
    // only one jar as project
    if(projectList.length != 1){
      throw new IllegalStateException("Task1")
    }
    projectList.head
  }

  /**
   * Prints the result of this analysis into a file with [[targetPath]] as its path.
   */
  private def printResult(output: String): Unit = {
    // Specify the file name
    val fileName = "Task2.txt"
    // Create a PrintWriter to write to the file
    val writer = new PrintWriter(targetPath + "/" + fileName)
    // Write content to the file
    writer.println(output)
    // Close the writer to release resources
    writer.close()
  }
}

