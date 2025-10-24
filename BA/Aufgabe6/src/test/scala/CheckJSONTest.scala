import JsonFormats.defaultRuleFormat
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json.{JsError, JsSuccess, Json}
import task6.jsonInput

import java.io.File
import scala.collection.mutable
import scala.io.Source
import scala.util.Using

class CheckJSONTest extends AnyFunSuite {

  var jsonInput: Specification = _

  /** [[CheckJSON]] */
  test("CheckJSON - with valid inputs"){
    val projectFile = Array(new java.io.File("src\\test\\scala\\Analyse1\\application-1.jar"))
    val libraryFile = Array(new java.io.File("src\\test\\scala\\Analyse1\\Lib\\utility-1.jar"))
    val isLibrary = false
    val file = new java.io.File("src/test/scala/Analyse1/Spezifikation.json")
    createJson(file)
    // check json
    val projectManager = new projectManager(projectFile, libraryFile, isLibrary)
    val checker = new CheckJSON()
    checker.check(jsonInput, projectManager)

    val map : mutable.LinkedHashMap[(String, String), (String, Int)] = mutable.LinkedHashMap()
    map += (null,null) -> ("FORBIDDEN",-1)
    map += ("org.example.application.core","org.example.utility.core.Utility") -> ("ALLOWED",0)
    map += ("org.example.application.core","org.example.application.services") -> ("ALLOWED",0)
    map += ("org.example.application.core","org.example.application.services.Service") -> ("FORBIDDEN",1)
    map += ("org.example.application.services.Service","org.example.utility.core.MathUtils") -> ("ALLOWED",0)
    map += ("org.example.application.services","org.example.utility.helpers.StringUtils") -> ("ALLOWED",0)

    assert(checker.ruleMapper==map)
  }

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
}
