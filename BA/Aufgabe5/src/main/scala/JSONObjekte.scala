import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue, Json, OFormat}

case class DeadCodeReport(filesAnalyzed: List[String], domainUsed: String, timeFinished: DateTime, totalRuntimeMs: Long, methodsFound: List[MethodWithDeadCode])
case class MethodWithDeadCode(fullSignature: String, numberOfTotalInstructions: Int, numberOfDeadInstructions: Int, enclosingTypeName: String, deadInstructions: List[DeadInstruction])
case class DeadInstruction(stringRepresentation: String, programCounter: Int)

object MethodWithDeadCode {
  implicit val deadInstructionFormat: OFormat[DeadInstruction] = Json.format[DeadInstruction]
  implicit val methodWithDeadCodeFormat: OFormat[MethodWithDeadCode] = Json.format[MethodWithDeadCode]
}

object DeadCodeReport {
  // import defined DateTime-Format - dont delete unused import (doesnt work without it)
  import DateTimeFormat._
  implicit val deadCodeReportFormat: OFormat[DeadCodeReport] = Json.format[DeadCodeReport]
}

object DateTimeFormat {
  implicit val dateTimeFormat: Format[DateTime] = new Format[DateTime] {
    def reads(json: JsValue): JsResult[DateTime] = json match {
      case JsString(s) => JsSuccess(ISODateTimeFormat.dateTimeParser().parseDateTime(s))
      case _ => JsError("Invalid DateTime format")
    }

    def writes(dateTime: DateTime): JsValue = JsString(dateTime.toString)
  }
}
