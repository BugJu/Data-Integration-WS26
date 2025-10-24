package Task4

import Util.ComparableObject
import org.opalj.ba.toDA
import org.opalj.bc.Assembler
import org.opalj.br._
import org.opalj.br.instructions._
import java.nio.file.{Files, Paths}
import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

/** Creates a new class, that contains only by the main project used methods. */
class CreateClasses(filePath: String, targetPath: String) {

  /**
   * Creates a new class, that contains only the methods used by the main project.
   * @param cf The classFile we want to copy.
   */
  def createClass(cf: ListBuffer[ComparableObject]): ClassFile = {
    // creates new class
    val newCF = createCode(cf)
    // finalizes for saving
    val rawClassFile: Array[Byte] = Assembler(toDA(newCF))
    // store file on disk
    Files.createDirectories(Paths.get(s"$targetPath/Task4_2"))
    Files.write(Paths.get(s"$targetPath/Task4_2/${cf.head.storeClassFile.fqn.split("/").last.replace("$", "")}}.class"), rawClassFile)
    newCF
  }

  /** Creates new class file without unused methods. */
  private def createCode(classFileObject: ListBuffer[ComparableObject]): ClassFile = {
    val cf = classFileObject.head.storeClassFile

    val newMethods = classFileObject
      .map{ method =>
        val code = method.storeMethod.body.get
        // New instructions must take care of proper stack layout - this method will not auto-layout anything
        val newInstructions = buildNewInstructionArrayFrom(method.storeMethod.descriptor.returnType)
        // Remove exception handlers for stack validity - may change code behavior
        val m = method.storeMethod
        m.copy(body = Some(code.copy(instructions = newInstructions, exceptionHandlers = NoExceptionHandlers)))
      }
    val test = ArraySeq(newMethods.toSeq: _*)
    cf.copy(methods = test)
  }

  /**
   * Creates the needed return type
   * @param returnType The return type needed.
   * @return Instructions
   */
  def buildNewInstructionArrayFrom(returnType: Type): Array[Instruction] = {
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
