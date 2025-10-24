package Task6

import JSONObject.JSON._
import play.api.libs.json.{JsError, JsSuccess, Json}

import java.io.{File, PrintWriter}
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.util.Using

/** Object */
object task6_1 {

  /** JSON-File as a usable object of [[Specification]] */
  var jsonInput: Specification = _
  /** Stores the file of the project. */
  var projectFile: Array[File] = _
  /** Stores the files of the libraries. */
  var libraryFile: Array[File] = _
  /** Boolean value to check that project is a library or not (important for starting point) */
  var isLibrary: Boolean = _

  var targetPath: String = _

  /** Starting point of our analysis. */
  def main(jsonInput: Specification, isLibrary: Boolean, projectFile: Array[File], libraryFiles: Array[File], targetPath: String): Unit = {
    // read input
    this.jsonInput = jsonInput
    this.isLibrary = isLibrary
    this.projectFile = projectFile
    this.libraryFile = libraryFiles
    this.targetPath = targetPath

    // check json
    val projectManager = new projectManager(projectFile, libraryFile, isLibrary)
    val checkJSON = new CheckJSON()
    checkJSON.check(jsonInput, projectManager)

    // analyse
    val ana = new Analysis()
    ana.startAnalysis(projectManager)

    // write and output
    printResult(checkJSON.getResult() + ana.getViolations() + ana.getResult())
  }

  /**
   * Create a [[Specification]] object and save it in [[jsonInput]].
   * @param file The found json file which should be the specification for this analysis.
   */
  def createJson(file: File): Unit = {
    import JsonFormats._

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

  /** Used for testing */
  def clear(): Unit = {
    this.isLibrary = false
    this.projectFile = null
    this.libraryFile = null
    this.jsonInput = null
  }

  /**
   * Prints the result of this analysis into a file with [[targetPath]] as its path.
   */
  private def printResult(output: String): Unit = {
    // Specify the file name
    val fileName = "Task6.txt"
    // Create a PrintWriter to write to the file
    val writer = new PrintWriter(targetPath + "/" + fileName)
    // Write content to the file
    writer.println(output)
    // Close the writer to release resources
    writer.close()
  }
}
