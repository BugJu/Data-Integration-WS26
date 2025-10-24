import play.api.libs.json.{JsError, JsSuccess, Json}
import JsonFormats._

import java.io.{File, PrintWriter}
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.util.Using

// filePath=src/main/scala/Analysis/Analyse2 isLibrary=false
// sbt clean coverage test coverageReport
/** Object */
object task6 {

  /** JSON-File as a usable object of [[Specification]] */
  var jsonInput: Specification = _
  /** Stores the file of the project. */
  var projectFile: Array[File] = _
  /** Stores the files of the libraries. */
  var libraryFile: Array[File] = _
  /** Boolean value to check that project is a library or not (important for starting point) */
  var isLibrary: Boolean = _

  /** Starting point of our analysis. */
  def main(args : Array[String]): Unit = {
    // read input
    if(args.head.contains("filePath")){
      readInput(args.head, args(1))
    }
    else{
      readInput(args(1), args.head)
    }

    // check json
    val projectManager = new projectManager(projectFile, libraryFile, isLibrary)
    new CheckJSON().check(jsonInput, projectManager)

    // analyse
    val ana = new Analysis()
    ana.startAnalysis(projectManager)

    // write and output
    writeAndOutput(ana.getViolations())
  }

  /**
   * Reads the input given in the CLI.
   *
   * @param filePath Should consist of the following commands: filePath="PathToFiles" (maybe add path to specifications)
   * @throws IllegalArgumentException Exception thrown when basic checks fail for the commands given or the json file
   *                                  can't be created.
   */
  def readInput(filePath : String, isLibrary : String): Unit = {
    // checks for correct command-structure
    if(!filePath.contains("filePath")){
      throw new IllegalArgumentException(f"Wrong command given. Should be: 'filePath=PathToFiles', but was: $filePath")
    }
    // checks that folder given in the cmd actually exists
    val pathToFiles = new java.io.File(filePath.split("=").last)
    if(!pathToFiles.isDirectory){
      throw new IllegalArgumentException(f"Check the given path: ${pathToFiles.toPath}")
    }

    // ----- read json file ----- //
    val jsonTXT = pathToFiles.listFiles().filter{file => file.getName.endsWith(".json")}
    if(jsonTXT.length != 1){
      throw new IllegalArgumentException(f"Found more or none json-File for specifications: ${jsonTXT.length}")
    }
    // create json
    createJson(jsonTXT.head)

    // ----- set projectFiles ----- //
    projectFile = pathToFiles.listFiles().filter{file => file.getName.endsWith(".jar")}
    if(projectFile.length != 1){
      throw new IllegalArgumentException(f"Found more or none jar-File for projects: ${projectFile.length}")
    }

    // ----- set libraryFiles ----- //
    val libDirectory = pathToFiles.listFiles().filter{file => file.isDirectory}
    if(libDirectory.length != 1){
      throw new IllegalArgumentException(f"Found more or none lib-Directories: ${libDirectory.length}")
    }
    libraryFile = libDirectory.head.listFiles().filter{file => file.getName.endsWith(".jar")}

    // ----- is library ----- //
    this.isLibrary = isLibrary.split("=").last.toBoolean
  }

  /**
   * Create a [[Specification]] object and save it in [[jsonInput]].
   * @param file The found json file which should be the specification for this analysis.
   */
  def createJson(file: File): Unit = {
    // Read JSON file and parse it into DefaultRule object
    val result: Either[String, Specification] = Using(Source.fromFile(file.getPath)) { source =>
      val jsonString = source.getLines().mkString
      Json.parse(jsonString).validate[Specification] match {
        case JsSuccess(obj, _) => Right(obj)
        case JsError(errors)   => Left(s"Failed to parse JSON: $errors")
      }
    }.getOrElse(Left("Error reading the file"))
    result match {
      case Right(defaultRuleObj) => jsonInput = defaultRuleObj
      case Left(error) => throw new IllegalArgumentException(s"Something went wrong while reading the specification: $error")
    }
  }

  /**
   * Writes result in a file and prints it into the console.
   * @param output result content
   */
  def writeAndOutput(output: ListBuffer[String]): Unit = {
    output.foreach(println(_))

    val filePath = projectFile.head.getPath.split('\\').array.dropRight(1).mkString("/") + "/Result.txt"

    Using(new PrintWriter(filePath)) { writer =>
      output.foreach{
        writer.println
      }
    }
  }

  /** Used for testing */
  def clear(): Unit = {
    this.isLibrary = false
    this.projectFile = null
    this.libraryFile = null
    this.jsonInput = null
  }
}
