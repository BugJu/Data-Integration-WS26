package Task6

import JSONObject.JSON.{JsonFormats, Root, Specification}
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json.{JsError, JsResult, JsSuccess, JsValue, Json}

import java.nio.file.{Files, Paths}

class AnalyseTest extends AnyFunSuite{


  /** [[Analysis]] */
  test("Analyse - with valid inputs"){
    import JsonFormats._
    val filePath = new java.io.File("src/test/scala/Task6/Analyse1/Spezifikation.json")
    val fileContent = new String(Files.readAllBytes(Paths.get(filePath.getPath)))
    val json: JsValue = Json.parse(fileContent)
    val rootResult: JsResult[Specification] = json.validate[Specification]
    val specification: Specification = rootResult.get
    Task6.init(specification, false, "src/test/scala/Analyse1", "src/test/scala/Analyse1" )
  }
}
