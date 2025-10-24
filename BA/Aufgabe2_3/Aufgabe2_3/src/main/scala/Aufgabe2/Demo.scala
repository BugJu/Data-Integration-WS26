package Aufgabe2

import org.opalj.br.ObjectType
import org.opalj.br.analyses.Project
import org.opalj.br.instructions.MethodInvocationInstruction

import java.net.URL
import scala.collection.mutable.ListBuffer

object Demo {

  /** Inspect from the file given as the first parameter in the console, if it has the methods given in the other parameter
   *  comment e.g.: Test.jar System.getSecurityManager System.setSecurityManager
   *
   * @param args Parameter for the input
   */
  def main(args: Array[String]): Unit = {
    // change Array to ListBuffer
    val input: ListBuffer[String] = args.to(ListBuffer)
    // filePath for the project creation
    val filePath: String = input.head
    // remove first element - filePath
    input.remove(0)

    // read input
    val searchedMethod: ListBuffer[String] = readInput(input)

    // create opal project
    val project = createProject(new java.io.File(s"src//main//scala//Aufgabe2//$filePath"))

    // init. variable
    var wasFoundInClass: Boolean = false
    var foundMethod: String = ""
    var output: String = ""
    var startOutput: String = ""

    println(s"\nGiven FilePath: $filePath, Methods: ${searchedMethod.toString()}\n")

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
    println(output)
  }



  /**
   * Reads the Input from args.
   *
   * @param args Input from console.
   * @return ListBuffer with every parameter from the console.
   */
  private def readInput(args: ListBuffer[String]): ListBuffer[String] = {
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
    args
  }

  /**
   * Used to identify if instruction is calling one of the searched methods.
   *
   * @param className Class which calls the method
   * @param methodName Name of the called method.
   * @param searchedMethods A list of the searchedMethods
   * @return Boolean type; Information for calling method if instruction was actually using searched method.
   */
  private def methodCalled(className : String, methodName: String, searchedMethods : ListBuffer[String]): String = {
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
    val project = Project(file)

    // valid file?
    if (project.allProjectClassFiles.isEmpty) {
      throw new IllegalStateException("The file at the given path is not a valid file.")
    }
    // return
    project
  }
}
