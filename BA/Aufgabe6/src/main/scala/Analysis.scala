import org.opalj.br.ObjectType
import org.opalj.br.analyses.Project
import org.opalj.br.instructions.{INVOKESPECIAL, MethodInvocationInstruction}
import org.opalj.tac.cg.{CallGraph, XTACallGraphKey}

import java.net.URL
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class Analysis {

  /** Created Project */
  private var project: Project[URL] = _
  /** Created CallGraph - only uses [[XTACallGraphKey]]  */
  private var callGraph: CallGraph = _
  /** Holds a Map for all [[Rule]]s. Made by [[CheckJSON]]. */
  private var ruleMapper: mutable.LinkedHashMap[(String, String), (String, Int)] = _
  /** [[projectManager]] */
  private var pManager: projectManager = _
  /** List of all warnings */
  private var violations: ListBuffer[String] = ListBuffer()

  /** Starts the analysis. */
  def startAnalysis(projectManager: projectManager): Unit = {
    val triple = projectManager.getProjectAndCallGraphAndRuleMapper
    pManager = projectManager
    project = triple._1
    callGraph = triple._2
    ruleMapper = triple._3
    //ruleMapper.foreach(println(_))

    // find violations
    findViolations()

    // output
    println("Violations found:")
    // Gruppieren und Zählen
    val counts = violations.groupBy(identity).view.mapValues(_.size).toMap

    // Reihenfolge der Eingabeliste beibehalten und Zahlen anhängen
    val seen = scala.collection.mutable.Set[String]()
    val result = violations.flatMap { item =>
      if (!seen.contains(item)) {
        seen += item
        Some(s"$item${counts(item)}") // Nur das erste Auftreten hinzufügen
      } else {
        None // Überspringe spätere Vorkommen
      }
    }
    violations = result
  }

  private def findViolations(): Unit = {
    // 1.2.1 called Methods for every Method
    /*
    callGraph.reachableMethods().filter{ele => pManager.getPackageOfClass(ele.method.declaringClassType.fqn) != null}.foreach { called =>
      callGraph.callersOf(called.method).iterator.toList.foreach{ method =>
        val from = method._1.declaringClassType.fqn.replace("/", ".")
        val to = called.method.declaringClassType.fqn.replace("/", ".")
        checkFrom(from, to, method._1.name, called.method.name)
      }
    }
     */
    project.allClassFiles.foreach(cf =>
      cf.methodsWithBody.foreach( method =>
        method.body.get.instructions.filter(instr => instr != null && instr.toString.contains("INVOKE")).foreach {
          // check if given instr. call/ creates one of the searched methods
          case instr@(invocation: INVOKESPECIAL) =>
            invocation.declaringClass match {
              case objectType: ObjectType =>
                if(pManager.packageOrClassExists(objectType.fqn.replace("/", "."))){
                  val from = (cf.fqn+"/"+method.name).replace("/", ".")
                  val to = objectType.fqn.replace("/", ".")
                  //println(s"From: $from, To: $to")
                  checkFrom(from, to, method.name, objectType.fqn, "SPECIAL")
                }
              case _ =>
            }
          case instr@(invocation: MethodInvocationInstruction) =>
            invocation.declaringClass match {
              case objectType: ObjectType =>
                if(pManager.packageOrClassExists(objectType.fqn.replace("/", "."))){
                  //println(instr)
                  val from = (cf.fqn+"/"+method.name).replace("/", ".")
                  val to = objectType.fqn.replace("/", ".")
                  //println(s"From: $from, To: $to")
                  checkFrom(from, to, method.name, objectType.fqn)
                }
              case _ =>
            }
          case _ => //
        }
      )
    )

    // 2.1 all fields - maybe duplicates with before found
    println("--------------------------------------")
    violations += "\nOnly important if a field is is not set."
    //println("Fields:")
    val fields = project.allFields.toList
    fields.filter{field => pManager.isClass(field)}.foreach{ field =>
      val from = field.classFile.fqn.replace("/", ".")+ "." +field.name
      var to = field.fieldType.toJava
      if(field.attributes.nonEmpty){
        to = pManager.cutClassType(field.attributes.toString()).replace("/", ".")
      }
      to = to.replace("[]", "")
      //println(s"From: $from, To: $to")
      checkFrom(from, to, isField = "Field ")
    }
  }

  /** Checks for valid 'from' in [[ruleMapper]] for the given methods.*/
  private def checkFrom(fromO: String, toO: String, methodName: String = null, calledNamed: String = null, isField: String = "Method"): Unit = {
    var from = fromO
    val to = toO
    from = fromO.split('.').dropRight(1).mkString(".")
    //println(s"From: $from $methodName, To: $to $calledNamed")
    var checked = ""
    // check for class
    if(ruleMapper.exists(ele => ele._1._1 == from)){
      checked = checkTo(from, to)
    }
    // check for package - only if no valid check yet
    from = pManager.getPackageOfClass(from).head
    if(checked=="" && ruleMapper.exists(ele => ele._1._1 == from.replace("/", "."))){
      checked = checkTo(from, to)
    }
    // check for jar - only if no valid check yet
    from = pManager.getJarToPackage(from).head
    if(checked=="" && ruleMapper.exists(ele => ele._1._1 == from.replace("/", "."))){
      checked = checkTo(from, to)
    }
    // check if no validation yet with default rule
    if(checked=="" && ruleMapper(null, null)._1=="FORBIDDEN"){
      checked = s"Rule: DefaultRule "
    }
    if(checked != "" && checked != "No"){
      violations += s"$isField: From '$fromO', To: '$toO' " + checked
    }
  }

  /** Checks for valid to 'tos' for given 'from' */
  private def checkTo(from: String, to: String): String = {
    var newTo = to
    // to class
    var typ = ruleMapper.getOrElse((from.replace("/", "."), newTo), null)
    if(typ != null){
      if(typ._1 == "FORBIDDEN") return s"Rule: From '${from.replace("/", ".")}, To: '$newTo' "
      else return "No"
    }
    // to package
    newTo = pManager.getPackageOfClass(newTo).head.replace("/", ".")
    typ = ruleMapper.getOrElse((from, newTo), null)
    if(typ != null){
      if(typ._1 == "FORBIDDEN") return s"Rule: From '${from.replace("/", ".")}, To: '$newTo' "
      else return "No"
    }
    // to jar
    newTo = pManager.getJarToPackage(newTo).head.replace("/", ".")
    typ = ruleMapper.getOrElse((from, newTo), null)
    if(typ != null){
      if(typ._1 == "FORBIDDEN") return s"Rule: From '${from.replace("/", ".")}, To: '$newTo' "
      else return "No"
    }
    ""
  }

  def getViolations(): ListBuffer[String] = {
    violations
  }
}
