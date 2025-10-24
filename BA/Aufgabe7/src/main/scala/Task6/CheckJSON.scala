package Task6

import JSONObject.JSON.{Rule, Specification}

import scala.collection.mutable

class CheckJSON {

  /** Reference to [[projectManager]]. */
  private var pManager: projectManager = _
  /** Holds all rules for duplication recognition and later analysis. */
  val ruleMapper: mutable.LinkedHashMap[(String, String), (String, Int)] = mutable.LinkedHashMap()

  var result: String = _

  /**
   * Checks that json file is valid.
   * @param defaultRule The input of the json file.
   * @param projectManager The information for the project structure to validate.
   */
  def check(defaultRule: Specification, projectManager: projectManager): Unit = {
    pManager = projectManager
    val dRule = defaultRule.defaultRule
    // correct defaultRule
    if(dRule != "FORBIDDEN" && dRule != "ALLOWED"){
      throw new IllegalArgumentException(s"The default rule for this specification needs to be set to 'FORBIDDEN' or 'ALLOWED' - Found: $dRule")
    }
    // rules are empty
    val rules = defaultRule.rules
    ruleMapper += ((null, null) -> (dRule, -1))
    result += "WARNINGS:\n"
    if(rules.nonEmpty){
      // analyse all rules
      rules.foreach{ rule =>
        checkRule(null, null, dRule, rule, 0)
      }
    }
    result += "--------------------------------------\n"
    pManager.setRuleMapper(ruleMapper)
    //ruleMapper.foreach(println(_))
  }

  /**
   * Checks that the given [[Rule]] is valid.
   * @param lastFrom The [[Rule.from]] from the last rule.
   * @param lastTo The [[Rule.to]] from the last rule.
   * @param lastType The [[Rule.type]] from the last rule.
   * @param rule The given [[Rule]].
   * @param depth The depth of this [[Rule]] - important for later analysis.
   */
  private def checkRule(lastFrom: String, lastTo: String, lastType: String, rule: Rule, depth: Int): Unit = {
    // first rule set
    if(lastFrom == null){
      checkFirstRule(rule)
    }
    // if this rule is an 'except' rule
    else{
      checkExceptRule(lastFrom, lastTo, rule)
    }
    // ----- type ----- //
    val typ: String = rule.`type`
    if(lastType == "FORBIDDEN" && typ != "ALLOWED"){
      throw new IllegalArgumentException(s"'type' should be 'ALLOWED' here - Found: ${rule.`type`}")
    }
    else if(lastType == "ALLOWED" && typ != "FORBIDDEN"){
      throw new IllegalArgumentException(s"'type' should be 'FORBIDDEN' here - Found: ${rule.`type`}")
    }
    // ----- check duplication ----- //
    checkDuplication(rule, depth)
    // ----- except? ----- //
    rule.except match{
      case Some(rules) => rules.foreach(checkRule(rule.from, rule.to, rule.`type`, _, depth+1))
      case None =>
    }
  }

  /**
   * Checks for duplications and possible big problems like different 'type's set
   * @param rule The [[Rule]] to check.
   * @throws IllegalArgumentException Exception if the rule is already mapped and has a different [[Rule.`type`]].
   */
  private def checkDuplication(rule: Rule, depth: Int): Unit = {
    val foundTyp = this.ruleMapper.getOrElse((rule.from, rule.to), null)
    // pair doesnt exist yet
    if(foundTyp==null) ruleMapper += ((rule.from, rule.to) -> (rule.`type`, depth))
    // pair already exists
    else {
      // only a problem if type is different
      if(foundTyp._1 != rule.`type`){
        throw new IllegalArgumentException(s"This rule already exists and has a different 'type': $rule")
      }
      else{
        result += s"This rule already exists, but has the same type (so no problem): $rule"
      }
    }
  }

  /** This is used for rules that are [[Rule.except]]. */
  private def checkExceptRule(lastFrom: String, lastTo: String, rule: Rule): Unit = {
    // same from and to as before
    if(lastFrom==rule.from && lastTo==rule.to) result += s"This rule is the same as the rule before: $rule"
    // same from
    if(lastFrom == rule.from){
      if(!pManager.validate(lastTo, rule.to)) result += s"The rule is not valid because of no valid 'to' value: $rule"
    }
    // same to
    else if (lastTo == rule.to){
      if(!pManager.validate(lastFrom, rule.from)) result += s"The rule is not valid because of no valid 'from' value: $rule"
    }
    // both different
    else {
      if(!pManager.validate(lastTo, rule.to) && !pManager.validate(lastFrom, rule.from))
        result += s"The rule is not valid because of no valid 'from' or/and 'to' value: $rule"
    }
  }

  /** This is used for rules that are no exception. */
  private def checkFirstRule(rule: Rule): Unit = {
    // ----- from ----- //
    // jar - exists
    if(rule.from.contains(".jar")){
      if(!pManager.jarExists(rule.from)) result += s"Rule from is set wrong: $rule"
    }
    // package or class
    else{
      if(!pManager.packageOrClassExists(rule.from)) result += s"Rule from is set wrong: $rule"
    }

    // ----- to ----- //
    // jar - exists
    if(rule.to.contains(".jar")){
      if(!pManager.jarExists(rule.from)) result += s"Rule to is set wrong: $rule"
    }
    // package or class
    else{
      if(!pManager.packageOrClassExists(rule.to)) result += s"Rule to is set wrong: $rule"
    }
  }

  def getResult(): String = result
}
