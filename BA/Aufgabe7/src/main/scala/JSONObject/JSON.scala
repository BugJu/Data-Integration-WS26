package JSONObject

import Task1.Task1
import Task2.Task2
import play.api.libs.json._

import java.io.File
import java.nio.file.{Files, Paths}
import scala.collection.mutable.ListBuffer

object JSON {

  case class Limits(
                     setMethodLimit: Int,
                     setFieldLimit: Int,
                     setInstructionLimit: Int,
                     setMaxCohesion: Double,
                     setMaxCoupling: Int,
                     setMaxPropertiesFalse: Int
                   )

  case class Task1JSON(limits: Limits)
  case class Task2JSON(methods: Array[String])
  case class Task3JSON(callgraphkey: String)
  case class Task4JSON(callgraphkey: String, searchFor: Array[String])
  case class Task5JSON(domainIndex: Int)
  case class Task6JSON(specification: Specification)

  case class Root(filePath: String, targetPath: String, isLibrary: Boolean, task1: Task1JSON, task2: Task2JSON, task3: Task3JSON, task4: Task4JSON, task5: Task5JSON, task6: Task6JSON)

  case class Config(task1: Boolean, task2: Boolean, task3: Boolean, task4: Boolean, task5: Boolean, task6: Boolean)

  /** Basis for the json
   * @param defaultRule Is the default rule set. Can be 'FORBIDDEN' or 'ALLOWED'.
   * @param rules List of [[Rule]]s. Can also be used to specify other rules.
   */
  case class Specification(defaultRule: String, rules: List[Rule])
  /**
   *  A rule that is set in this specification.
   * @param from Which package/ jar/ class this rule is defined for.
   * @param to Which package/ jar/ class is allowed or forbidden to use.
   * @param `type` 'ALLOWED' or 'FORBIDDEN'
   * @param except Can be empty or another [[Rule]] that specifies this [[Rule]].
   */
  case class Rule(from: String, to: String, `type`: String, except: Option[List[Rule]] = None)

  object JsonFormats {
    implicit val limitsFormat: Format[Limits] = Json.format[Limits]
    implicit val task1Format: Format[Task1JSON] = Json.format[Task1JSON]
    implicit val task2Format: Format[Task2JSON] = Json.format[Task2JSON]
    implicit val task3Format: Format[Task3JSON] = Json.format[Task3JSON]
    implicit val task4Format: Format[Task4JSON] = Json.format[Task4JSON]
    implicit val task5Format: Format[Task5JSON] = Json.format[Task5JSON]
    implicit val ruleFormat: Format[Rule] = Json.format[Rule]
    implicit val defaultRuleFormat: Format[Specification] = Json.format[Specification]
    implicit val task6Format: Format[Task6JSON] = Json.format[Task6JSON]
    implicit val rootFormat: Format[Root] = Json.format[Root]

    implicit val configFormat: Format[Config] = Json.format[Config]
  }

  private var json: Root = _

  private var config: Config = _

  def build(path: String): Unit = {
    import JsonFormats._

    val filePath = s"${path}TaskConfig.json"
    val fileContent = new String(Files.readAllBytes(Paths.get(filePath)))

    val json: JsValue = Json.parse(fileContent)

    val rootResult: JsResult[Root] = json.validate[Root]

    rootResult match {
      case JsSuccess(parsedRoot, _) =>
        //println(s"Erfolgreich eingelesen: $parsedRoot")
        this.json = parsedRoot
      case JsError(errors) =>
        //println(s"Fehler beim Parsen: $errors")
    }

    readConfig(path)

    // init. all tasks
    val limits = this.json.task1.limits
    Task1.init(List[String](limits.setMethodLimit.toString, limits.setFieldLimit.toString, limits.setInstructionLimit.toString,
      limits.setMaxCohesion.toString, limits.setMaxCoupling.toString, limits.setMaxPropertiesFalse.toString), this.json.filePath,
      this.json.targetPath)
    Task2.init(this.json.task2.methods.toList, this.json.filePath, this.json.targetPath)
    Task3.Task3.init(this.json.task3.callgraphkey, this.json.isLibrary, this.json.filePath, this.json.targetPath)
    Task4.Task4.init(this.json.task4.callgraphkey, this.json.isLibrary, this.json.task4.searchFor.toList, this.json.filePath,
      this.json.targetPath)
    Task5.Task5.init(this.json.task5.domainIndex, this.json.filePath, this.json.targetPath)
    Task6.Task6.init(this.json.task6.specification, this.json.isLibrary, this.json.filePath, this.json.targetPath)
  }
  /**  */
  private def readConfig(path: String): Unit = {
    import JsonFormats._
    val filePath = s"${path}Config.json"
    val fileContent = new String(Files.readAllBytes(Paths.get(filePath)))

    val json: JsValue = Json.parse(fileContent)

    val rootResult: JsResult[Config] = json.validate[Config]

    rootResult match {
      case JsSuccess(parsedConfig, _) =>
        //println(s"Erfolgreich eingelesen: $parsedConfig")
        this.config = parsedConfig
      case JsError(errors) =>
        //println(s"Fehler beim Parsen: $errors")
    }
  }

  def getConfig: List[Boolean] = {
    val list: ListBuffer[Boolean] = ListBuffer()
    list.addOne(this.config.task1)
    list.addOne(this.config.task2)
    list.addOne(this.config.task3)
    list.addOne(this.config.task4)
    list.addOne(this.config.task5)
    list.addOne(this.config.task6)
    list.toList
  }

  def getTargetPath(): File = {
    new java.io.File(this.json.targetPath)
  }
}
