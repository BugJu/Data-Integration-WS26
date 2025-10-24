package Task1

import org.opalj.br.analyses.Project
import org.opalj.br.instructions.{GETFIELD, MethodInvocationInstruction, PUTFIELD}
import org.opalj.br.{ClassFile, ObjectType}
import org.opalj.log.{Error, GlobalLogContext, LogContext, LogMessage, OPALLogger}

import java.io.{File, PrintWriter}
import scala.collection.mutable.ListBuffer

object Task1 {

  private var setMethodLimit: Int = _ // 40
  private var setFieldLimit: Int = _ // 15
  private var setInstructionLimit: Int = _ // 500
  private var setMaxCohesion: Double = _ // 0.8
  private var setMaxCoupling: Int = _ // 5
  private var setMaxPropertiesFalse: Int = _ // 4
  private var filePath: File = _
  private var targetPath: String = _

  /**
   * Used to set the limits.
   * @param task The found limits in the Config.json
   */
  def init(task: List[String], filePath: String, targetPath: String): Unit = {
    setMethodLimit = task.head.toInt
    setFieldLimit = task(1).toInt
    setInstructionLimit = task(2).toInt
    setMaxCohesion = task(3).toDouble
    setMaxCoupling = task(4).toInt
    setMaxPropertiesFalse = task(5).toInt
    this.filePath = getFile(new java.io.File(filePath))
    this.targetPath = targetPath
  }

  def analyse(): Unit = {
    val limits = Map("countMethodsLimit" -> setMethodLimit.toString, "countFieldsLimit" -> setFieldLimit.toString,
      "countInstrLimit" -> setInstructionLimit.toString, "countCohesionLimit" -> setMaxCohesion.toString, "countCouplingLimit" -> setMaxCoupling.toString, "maxWrong" -> setMaxPropertiesFalse.toString)

    // read file
    if(!filePath.exists()){
      throw new IllegalStateException("The file does not exist. Please check the set path.")
    }
    val logg: OPALLogger = new MyLogger
    OPALLogger.updateLogger(GlobalLogContext, logg)
    val p = Project(filePath)
    val classFiles = p.allProjectClassFiles
    if (classFiles.isEmpty) {
      throw new IllegalStateException("The file at the given path is not a valid file.")
    }

    // values to compare
    var countMethods = 0
    var countFields = 0
    var countInstr = 0
    // saves the counted numbers (properties)
    val classList = ListBuffer[Map[String, Any]]()
    // save for dependencies for every class
    var classDependencies = Map[String, Int]()
    p.allProjectClassFiles.foreach { classFile =>
      classDependencies += (classFile.thisType.fqn -> 0)
    }

    // start
    p.allProjectClassFiles.foreach { classFile =>
      // count methods
      countMethods = classFile.methodsWithBody.size

      // count fields
      countFields = classFile.fields.size

      // count instr
      var count = 0
      classFile.methodsWithBody.foreach { m =>
        if (m.body.isDefined) {
          val body = m.body.get
          count += body.instructionsCount
        }
      }
      countInstr = count

      // cohesion with LCOM2 (lack of cohesion of methods)
      val listOfListBuffer: List[ListBuffer[String]] = List.fill(countMethods)(ListBuffer[String]())
      classFile.methodsWithBody.filter{ele => !ele.isConstructor && ele.body.isDefined}.zipWithIndex.foreach { case (m, index) =>
        val body = m.body.get
        body.instructions.foreach { instr =>
          if (instr != null) {
            // GET class or PUT class
            instr match {
              case GETFIELD(_, fieldName, _) =>
                if (!listOfListBuffer(index).contains(fieldName)) {
                  listOfListBuffer(index) += fieldName
                }
              case PUTFIELD(_, fieldName, _) =>
                if (!listOfListBuffer(index).contains(fieldName)) {
                  listOfListBuffer(index) += fieldName
                }
              case _ => // nothing
            }
          }
        }
      }
      // field accesses for every method - SUM
      val access = countAccess(listOfListBuffer)
      // without constructor - all possible accesses (count methods * count fields)
      val possibleAccess = (countMethods-1)*countFields
      // if possibleAccess 0 (no fields or only constructor)
      var lcom = 0.0
      if(possibleAccess!=0){
        lcom = 1-(access/possibleAccess.toDouble)
      }

      accessOtherClass(classFile).toSet.foreach({ element: ObjectType =>
        if(classDependencies.contains(element.fqn)){
          classDependencies = classDependencies.updated(element.fqn, classDependencies.getOrElse(element.fqn, 0) + 1)
        }
      })

      // Map for every class
      val newMap = Map("classFile" -> classFile.fqn, "countMethods" -> countMethods, "countFields" -> countFields, "countInstr" -> countInstr, "countCohesion" -> lcom, "countDependencies" -> 0)
      classList += newMap
    }
    // coupling end - assign dependecies
    val updatedClassList = classList.map { test =>
      // create new map based on old map (with dependecies)
      test.updated("countDependencies", classDependencies(test("classFile").toString))
    }

    // Bounds:
    var output = s"Your set bounds:\n maxMethods: ${limits("countMethodsLimit")}\n maxFields: ${limits("countFieldsLimit")}\n maxInstructions: ${limits("countInstrLimit")}\n"+
      s" maxCohesion: ${limits("countCohesionLimit")}\n maxCoupling: ${limits("countCouplingLimit")}\n maxValidPropertiesOverBounds: ${limits("maxWrong")}\n"
    output += "Found Classes:\n"
    // check if godclass
    updatedClassList.foreach{ element =>
      var countWrongfulProperties = 0
      // methods
      countWrongfulProperties += isWrongful(limits("countMethodsLimit"), element("countMethods").asInstanceOf[Integer])
      // fields
      countWrongfulProperties += isWrongful(limits("countFieldsLimit"), element("countFields").asInstanceOf[Integer])
      // instructions
      countWrongfulProperties += isWrongful(limits("countInstrLimit"), element("countInstr").asInstanceOf[Integer])
      // cohesion
      countWrongfulProperties += isWrongful(limits("countCohesionLimit"), element("countCohesion").asInstanceOf[Double])
      // dependencies
      countWrongfulProperties += isWrongful(limits("countCouplingLimit"), element("countDependencies").asInstanceOf[Integer])
      if(countWrongfulProperties > limits("maxWrong").toInt){
        output += s"\t class: ${element("classFile")} is a godclass with your set bounds. method_count: ${element("countMethods")}, fields: ${element("countFields")}, instruktion: ${element("countInstr")}, cohesion: ${element("countCohesion")}, dependency: ${element("countDependencies")}\n"
      }
    }
    printResult(output)
 }

  private def accessOtherClass(classFile: ClassFile): ListBuffer[ObjectType] = {
    val currentClass = classFile.thisType.toString()
    val dependencies = ListBuffer[org.opalj.br.ObjectType]()
    classFile.methodsWithBody.foreach { m =>
      // ignore construct
      if (!m.isConstructor && m.body.isDefined) {
        val body = m.body.get
        body.instructions.foreach { instr =>
          if (instr != null) {
            // methods get and put
            instr match {
              // get field
              case GETFIELD(classType, _, _) => if (classType.toString() != currentClass) dependencies += classType
              // change field
              case PUTFIELD(classType, _, _) => if (classType.toString() != currentClass) dependencies += classType
              // init class
              case MethodInvocationInstruction(referenceType: org.opalj.br.ObjectType, _, _, _) => if (referenceType.toString() != currentClass) dependencies += referenceType
              case _ => //
            }
          }
        }
      }
    }
    dependencies
  }

  private def isWrongful(check: String, value: Int): Int = {
    if(check != "0"  && check.toInt < value){
      return 1
    }
    0
  }

  private def isWrongful(check: String, value: Double): Int = {
    if(check != "0" && check.toDouble < value){
      return 1
    }
    0
  }

  private def countAccess(list: List[ListBuffer[String]]): Int ={
    var counter = 0
    for (i <- 0 until list.size - 1) {
      counter += list(i).size
    }
    counter
  }

  /** Stops print into console when project is created. */
  private class MyLogger extends OPALLogger {
    override def log(message: LogMessage)(implicit ctx: LogContext): Unit = {
      message.level match {
        /*
        case Warn =>
          println(s"[WARN] ${message.message}")
         */
        case Error =>
          println(s"[ERR] ${message.message}")
        case _ =>
      }
    }
  }

  private def getFile(folder: File): File = {
    val filesList = folder.listFiles()
    val projectList = filesList.filter(file => file.getPath.split('.').last == "jar")
    // only one jar as project
    if(projectList.length != 1){
      throw new IllegalStateException("Task1")
    }
    projectList.head
  }

  /**
   * Prints te reslt of this analysis into a file wih [[targetPath]] as it path.
   */
  private def printResult(output: String): Unit = {
    // Specify the file name
    val fileName = "Task1.txt"
    // Create a PrintWriter to write to the file
    val writer = new PrintWriter(targetPath + "/" + fileName)
    // Write content to the file
    writer.println(output)
    // Close the writer to release resources
    writer.close()
  }
}
