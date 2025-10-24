package Task4


import Util.ComparableObject
import org.opalj.ba.toDA
import org.opalj.bc.Assembler
import org.opalj.bi._
import org.opalj.br._
import org.opalj.br.instructions._

import java.io.PrintWriter
import java.nio.file.{Files, Paths}
import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

/** Creates a new class, that is similar to the in [[modifyClass]] given class without the found instruction that call a searched method.
 * @param foundCallers The found callers.
 * @param searchFor The method we search for.*/
class ModifyClasses(foundCallers: ListBuffer[ComparableObject], searchFor: ListBuffer[ComparableObject], filePath: String, targetPath: String) {

  /** Dummies used */
  private val dummies: ListBuffer[ComparableObject] = ListBuffer[ComparableObject]()
  private val dummiesFlags: ListBuffer[AccessFlag] = ListBuffer[AccessFlag]()

  private var result: String = _

  /**
   * Creates a new class without the instruction call that use a searched method [[searchFor]].
   * @param classFile The classFile we want to copy.
   */
  def modifyClass(classFile: ClassFile): String = {
    // reset dummies - just to be sure
    dummies.clear()
    // creates new class
    val newCF = modifyCode(classFile)
    // finalizes for saving
    val rawClassFile: Array[Byte] = Assembler(toDA(newCF))
    // store file on disk
    Files.createDirectories(Paths.get(s"$targetPath/Task4_1"))
    Files.write(Paths.get(s"$targetPath/Task4_1/${classFile.fqn.split("/").last.replace("$", "")}.class"), rawClassFile)
    result
  }

  /**
   * Creates class without instruction we want to delete.
   * @param classFile Given class
   * @return Copy of given class without instructions that call [[searchFor]] methods.
   */
  private def modifyCode(classFile : ClassFile): ClassFile ={
    val cf = classFile

    val newMethods = cf
      .methods
      .map{ oldMethod =>
        if(foundCallers.exists(ele => ele.equals(oldMethod))){
          val oldCode = oldMethod.body.get
          // New instructions must take care of proper stack layout - this method will not auto-layout anything
          val newInstructions = buildNewInstructionArrayFrom(oldCode.instructions, classFile)
          // Remove exception handlers for stack validity - may change code behavior
          val m = oldMethod
          m.copy(body = Some(oldCode.copy(instructions = newInstructions, exceptionHandlers = NoExceptionHandlers)))
        } else { oldMethod.copy() }
      }

    // add dummies
    val addDummies: ListBuffer[MethodTemplate] = newMethods.to(ListBuffer)
    dummies.zipWithIndex.foreach{ dummy =>
      var method: MethodTemplate = null
      if(dummiesFlags(dummy._2) != null){
        method = Method(accessFlags = dummiesFlags(dummy._2).mask | ACC_PUBLIC.mask, name = s"${cf.fqn.toLowerCase()}${dummy._1.checkMethodName}", descriptor = dummy._1.checkMethodDescriptor, attributes = NoAttributes)
      }
      else{
        method = Method(accessFlags = ACC_PUBLIC.mask, name = s"${cf.fqn.toLowerCase()}${dummy._1.checkMethodName}", descriptor = dummy._1.checkMethodDescriptor, attributes = NoAttributes)
      }
      addDummies += method.copy(body = Some(Code.apply(maxStack = 2, maxLocals = 2, instructions = createInstr(dummy._1.checkMethodDescriptor.returnType), exceptionHandlers = NoExceptionHandlers, attributes = NoAttributes)))// .get.copy( instructions = Array[Instruction](RETURN)))
    }
    cf.copy(methods = ArraySeq(addDummies.toArray: _*))
  }

  /** Neutralizes ([[NOP]]) instructions that call something from [[searchFor]]. */
  private def buildNewInstructionArrayFrom(oldInstructions: Array[Instruction], classFile: ClassFile): Array[Instruction] = {
    val instruction: ListBuffer[Instruction] = ListBuffer[Instruction]()
    var newInstruction: Instruction = ACONST_NULL
    var output = s"-----${classFile.fqn}-----\n"
    oldInstructions.foreach{ instr =>
      if(instr !=null){
        instr match {
          case instr: INVOKESTATIC => newInstruction = testForSearchedMethod(instr, classFile)

          case instr: INVOKEDYNAMIC => newInstruction = testForSearchedMethod(instr, classFile)

          case instr: INVOKESPECIAL => newInstruction = testForSearchedMethod(instr, classFile)

          case instr: INVOKEVIRTUAL => newInstruction = testForSearchedMethod(instr, classFile)

          // prob. useless
          case instr: INVOKEINTERFACE => newInstruction = testForSearchedMethod(instr, classFile)

          case _ => newInstruction = instr
        }
      }
      else{
        newInstruction = instr
      }
      instruction += newInstruction
      // instr ---- new instruction
      output += s"$instr         $newInstruction\n"
      result += output
      output = ""
    }
    result += classFile.fqn + "\n"
    instruction.toList.foreach{result += _ + "\n"}
    instruction.toArray
  }

  /**
   * Check if given instr. call one of the searched methods [[searchFor]].
   *
   * @param instr Given instruction.
   * @param cf Given classFile.
   * @return
   */
  def testForSearchedMethod(instr: InvocationInstruction, cf: ClassFile): Instruction = {
    val found = searchFor.filter(ele => ele.equals(instr))
    // shouldnt happen
    if(found.size > 1){
      throw new IllegalStateException("Multiple ComparableObjects were found. Therefore this implementation very likely wrong.")
    }
    if(found.nonEmpty){
      // not already a dummy
      if(!dummies.exists(ele => ele.equals(found.head ))){
        dummies += found.head
      }
      instr match {
        case invoke : INVOKEVIRTUAL =>
          dummiesFlags += null
          INVOKEVIRTUAL.apply(declaringClass = s"${cf.thisType.fqn}", methodName = s"${cf.thisType.fqn.toLowerCase()}${invoke.name}", methodDescriptor = s"${invoke.methodDescriptor.toJVMDescriptor}").asInstanceOf[Instruction]

        case invoke : INVOKESTATIC =>
          dummiesFlags += ACC_STATIC
          INVOKESTATIC.apply(declaringClass = s"${cf.thisType.fqn}", isInterface = false, methodName = s"${cf.thisType.fqn.toLowerCase()}${invoke.name}", methodDescriptor = s"${invoke.methodDescriptor.toJVMDescriptor}")

        case invoke : INVOKESPECIAL =>
          dummiesFlags += null
          INVOKESPECIAL.apply(declaringClass = s"${cf.thisType.fqn}", isInterface = false, methodName = s"${cf.thisType.fqn.toLowerCase()}${invoke.name}", methodDescriptor = s"${invoke.methodDescriptor.toJVMDescriptor}")

        case invoke : INVOKEINTERFACE =>
          dummiesFlags += ACC_ABSTRACT
          INVOKEINTERFACE.apply(declaringClass = s"${cf.thisType.fqn}", methodName = s"${cf.thisType.fqn.toLowerCase()}${invoke.name}", methodDescriptor = s"${invoke.methodDescriptor.toJVMDescriptor}")

        case _ : INVOKEDYNAMIC =>
          Instruction.asInstanceOf[Instruction]
        case _ =>
          throw new IllegalStateException("How is this possible???");
      }
    }
    else{
      instr.asInstanceOf[Instruction]
    }
  }

  /** Gives the dummy the right return statement. */
  private def createInstr(returnType: Type): Array[Instruction] = {
    val instruction: ListBuffer[Instruction] = ListBuffer[Instruction]()
    if(returnType.isVoidType){
      instruction += RETURN
      instruction.toArray
    }
    // needs additional value for 'null'
    else {
      val instruction: ListBuffer[Instruction] = ListBuffer[Instruction]()
      // double
      if(returnType.isDoubleType){
        instruction += DCONST_0
        instruction += DRETURN
        instruction.toArray
      }
      // float
      else if(returnType.isFloatType){
        instruction += FCONST_0
        instruction += FRETURN
        instruction.toArray
      }
      // int
      else if(returnType.isIntegerType || returnType.isBooleanType || returnType.isByteType || returnType.isShortType || returnType.isCharType){
        instruction += ICONST_0
        instruction += IRETURN
        instruction.toArray
      }
      // long
      else if(returnType.isLongType){
        instruction += LCONST_0
        instruction += LRETURN
        instruction.toArray
      }
      else if(returnType.isReferenceType){
        instruction += ACONST_NULL
        instruction += ARETURN
        instruction.toArray
      }
      // Object
      else {
        throw new IllegalStateException(s"No matching type found: $returnType\n")
      }
    }
  }
}
