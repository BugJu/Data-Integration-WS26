import play.api.libs.json._

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
  implicit val ruleFormat: Format[Rule] = Json.format[Rule]
  implicit val defaultRuleFormat: Format[Specification] = Json.format[Specification]
}