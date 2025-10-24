import org.opalj.br.ClassFile
import org.opalj.br.analyses.Project
import org.opalj.br.instructions.Instruction

// field field methode, methode static, init. class
import org.opalj.br.instructions.{GETFIELD, PUTFIELD, MethodInvocationInstruction}
import org.opalj.br.ObjectType
import scala.collection.mutable.ListBuffer

import scala.io.StdIn

object Demo {

  def main(args: Array[String]): Unit = {
    // welcome message
    println("Welcome to this application. Below, you can set the limits for the properties on which you want to check for God classes.\n" +
      "You must set at least one limit (minimum of 0; <0 means unset). Finally, specify how many properties can fall outside the given limits before \n" +
      "the class is declared a God class. ")

    // setLimits
    var setMethodLimit = "40" // 40
    var setFieldLimit = "15" // 15
    var setInstructionLimit = "500" // 500
    var setMaxCohesion = "0.8" // 0.8
    var setMaxCoupling = "5" // 5
    var setMaxPropertiesFalse = "4" // 4

    // choose default or own limits
    val string_limits = s"MethodLimit: ${setMethodLimit}, FieldLimit: ${setFieldLimit}, InstructionLimit: ${setInstructionLimit}, MaxCohesion: ${setMaxCohesion}, setMaxCoupling: ${setMaxCoupling}, MaxPropertiesFalse: ${setMaxPropertiesFalse}}"
    var choose = StdIn.readLine(s"If you want to use the default limits enter 1 else enter 0 (Default: ${string_limits}): ")
    while(!isInt(choose) || choose.toInt < 0 || choose.toInt > 1){
      choose = StdIn.readLine(s"!WARNING (Wrong Input: ${choose})! Please enter 1 for default and 0 for your own input:")
    }
    // own limits
    var limits: Map[String, String] = Map()
    if(choose.toInt == 0){
      limits = this.setLimits()
    }
      // default
    else{
      if(!isInt(setMethodLimit.toString) || setMethodLimit.toInt < 0 || !isInt(setFieldLimit.toString) || setFieldLimit.toInt < 0 || !isInt(setInstructionLimit.toString) || setInstructionLimit.toInt < 0 ||
        !isDouble(setMaxCohesion.toString) || setMaxCohesion.toDouble < 0 || setMaxCohesion.toDouble > 1 || !isInt(setMaxCoupling.toString) || setMaxCoupling.toInt < 0){
        throw new IllegalStateException("One of the default limits has either wrong bounds or is not from the int type!")
      }
      val count = countLimitsSet(setMethodLimit.toString, setFieldLimit.toString, setInstructionLimit.toString, setMaxCohesion.toString, setMaxCoupling.toString)
      // if set limits are less than the max for a a class to be considered a godclass stop programm
      if(count<=setMaxPropertiesFalse.toInt || setMaxPropertiesFalse.toInt < 0){
        throw new IllegalStateException("Please change 'setMaxPropertiesFalse' if you want to use default limits or set atleast one limit!")
      }
      limits = Map("countMethodsLimit" -> setMethodLimit.toString, "countFieldsLimit" -> setFieldLimit.toString,
        "countInstrLimit" -> setInstructionLimit.toString, "countCohesionLimit" -> setMaxCohesion.toString, "countCouplingLimit" -> setMaxCoupling.toString, "maxWrong" -> setMaxPropertiesFalse.toString)
    }

    // read file
    //val jarFile = new java.io.File("pdfbox-2.0.24.jar")
    val jarFile = new java.io.File("Test.jar")
    // File exist?
    if(!jarFile.exists()){
      throw new IllegalStateException("The file does not exist. Please check the set path.")
    }
    val p = Project(jarFile)
    val classFiles = p.allProjectClassFiles
    if (!classFiles.nonEmpty) {
      throw new IllegalStateException("The file at the given path is not a valid file.")
    }

    // values to compare
    var countMethods = 0
    var countFields = 0
    var countInstr = 0
    // saves the counted numbers (properties)
    var classList = ListBuffer[Map[String, Any]]()
    // save for dependecies for every class
    var classDependencies = Map[String, Int]()
    p.allProjectClassFiles.foreach { classFile =>
      classDependencies += (classFile.thisType.fqn.toString -> 0)
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
      var listOfListBuffer: List[ListBuffer[String]] = List.fill(countMethods-1)(ListBuffer[String]())
      var index = 0;
      classFile.methodsWithBody.foreach { m =>
        // ignore constructor
        if (!m.isConstructor) {
          if (m.body.isDefined) {
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
          index += 1
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

      // copplung - only calculate dependencies
      var test = 0
      accessOtherClass(classFile).toSet.foreach({ element: ObjectType =>
        if(classDependencies.contains((element.fqn.toString))){
          classDependencies = classDependencies.updated(element.fqn.toString, classDependencies.getOrElse(element.fqn.toString, 0) + 1)
        }
      })

      // Map for every class
      val newMap = Map("classFile" -> classFile.fqn.toString, "countMethods" -> countMethods, "countFields" -> countFields, "countInstr" -> countInstr, "countCohesion" -> lcom, "countDependencies" -> 0)
      classList += newMap
    }
    // coupling end - assign dependecies
    val updatedClassList = classList.map { test =>
      // create new map based on old map (with dependecies)
      test.updated("countDependencies", classDependencies(test("classFile").toString))
    }

    // Bounds:
    println(s"Your set bounds - maxMethods ${limits("countMethodsLimit")}; maxFields ${limits("countFieldsLimit")}; maxInstructions ${limits("countInstrLimit")};"+
      s"maxCohesion ${limits("countCohesionLimit")}; maxCoupling ${limits("countCouplingLimit")}; maxValidPrpertiesOverBounds ${limits("maxWrong")}")
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
        println(s"\t class: ${element("classFile")} is a godclass with your set bounds. method_count: ${element("countMethods")}, fields: ${element("countFields")}, instruktion: ${element("countInstr")}, cohesion: ${element("countCohesion")}, dependency: ${element("countDependencies")}") //, coupling
      }
    }
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

  private def containsMindOneSimilar(list1: ListBuffer[String], list2: ListBuffer[String]): Boolean = {
    if(list1.isEmpty && list2.isEmpty){
      return true
    }
    if(list1.isEmpty || list2.isEmpty){
      return false
    }
    for{
      i <- 0 to list1.size-1
      j <- 0 to list2.size-1
      if list1(i).equals(list2(j))
    }{
      return true
    }
    return false
  }

  private def isInt(str: String): Boolean = {
    try {
        str.toInt
        return true
    } catch {
      case _: NumberFormatException => false
    }
  }

  private def isDouble(str: String): Boolean = {
    try {
      str.toDouble
      return true
    } catch {
      case _: NumberFormatException => false
    }
  }

  private def isWrongful(check: String, value: Int): Int = {
    if(check != "0"  && check.toInt < value){
      return 1
    }
    return 0
  }

  private def isWrongful(check: String, value: Double): Int = {
    if(check != "0" && check.toDouble < value){
      return 1
    }
    return 0
  }

  private def setLimits(): Map[String, String] = {
    var methods = "0"; var fields = "0"; var instructions = "0"; var cohesion = "0"; var coupling = "0"; var maxPropertiesFalse = "0"
    var count = 0
    // while not one property is set
    while(count == 0){
        while (!isInt({methods = StdIn.readLine("Enter the maximum number of methods a class should have:"); methods}) || methods.toInt < 0){
          println("Please enter the limit you want to set (e.g. 1, 2 or 0=unset):")
        }
        while (!isInt({fields = StdIn.readLine("Enter the maximum number of fields a class should have:"); fields}) || fields.toInt < 0){
          println("Please enter the limit you want to set (e.g. 1, 2 or 0=unset):")
        }
        while (!isInt({instructions = StdIn.readLine("Enter the maximum number of instructions a class should have:"); instructions}) || instructions.toInt < 0){
          println("Please enter the limit you want to set (e.g. 1, 2 or 0=unset):")
        }
        // no constructor
        while (!isDouble({cohesion = StdIn.readLine("Enter the maximum number the cohesion should have:"); cohesion}) || cohesion.toDouble >= 1 || cohesion.toDouble < 0){
          println("Please enter the limit you want to set (e.g. [0...,1] or 0=unset):")
        }
        while (!isInt({coupling = StdIn.readLine("Enter the maximum number the coupling should have:"); coupling}) || cohesion.toDouble < 0){
          println("Please enter the limit you want to set (e.g. 1, 2 or 0=unset):")
        }
        if(({count = countLimitsSet(methods, fields, instructions, cohesion, coupling); count}) == 0){
          println("Please set atleast one of the properties")
        }
      }
      while (!isInt({maxPropertiesFalse = StdIn.readLine("Enter the maximum number of properties not fulfilled ([0, "+(count-1)+"]):"); maxPropertiesFalse}) || maxPropertiesFalse.toInt >= count || maxPropertiesFalse.toInt < 0){
        println("Please enter a real positive (or 0) number with an integer type (e.g. 1, 2, 3) and in the interval ([0, "+(count-1)+"])")
      }
    return Map("countMethodsLimit" -> methods, "countFieldsLimit" -> fields, "countInstrLimit" -> instructions, "countCohesionLimit" -> cohesion, "countCouplingLimit" -> coupling, "maxWrong" -> maxPropertiesFalse)
  }

  private def countLimitsSet(methods: String, fields: String, instructions: String, cohesion: String, coupling: String): Int = {
    var count = 0
    if(methods.toInt>0){
      count += 1
    }
    if(fields.toInt>0){
      count += 1
    }
    if(instructions.toInt>0){
      count += 1
    }
    if(cohesion.toDouble>0){
      count += 1
    }
    if(coupling.toInt>0){
      count += 1
    }
    return count
  }

  private def countAccess(list: List[ListBuffer[String]]): Int ={
    var counter = 0
    for (i <- 0 until list.size - 1) {
      counter += list(i).size
    }
    return counter
  }
}
