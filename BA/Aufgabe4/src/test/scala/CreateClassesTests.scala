import org.opalj.br.instructions._
import org.opalj.br._
import org.scalatest.funsuite.AnyFunSuite

class CreateClassesTests extends AnyFunSuite{

  val intReturnType: Type = IntegerType
  val floatReturnType: Type = FloatType
  val doubleReturnType: Type = DoubleType
  val longReturnType: Type = LongType
  val voidReturnType: Type = VoidType
  val objectReturnType: Type = ObjectType("Test")

  test("buildNewInstructionArrayFrom"){
    val obj = new CreateClasses()
    // int
    assert(obj.buildNewInstructionArrayFrom(intReturnType).sameElements(Array[Instruction](ICONST_0, IRETURN)))
    // float
    assert(obj.buildNewInstructionArrayFrom(floatReturnType).sameElements(Array[Instruction](FCONST_0, FRETURN)))
    // double
    assert(obj.buildNewInstructionArrayFrom(doubleReturnType).sameElements(Array[Instruction](DCONST_0, DRETURN)))
    // long
    assert(obj.buildNewInstructionArrayFrom(longReturnType).sameElements(Array[Instruction](LCONST_0, LRETURN)))
    // void
    assert(obj.buildNewInstructionArrayFrom(voidReturnType).sameElements(Array[Instruction](RETURN)))
    // object
    assert(obj.buildNewInstructionArrayFrom(objectReturnType).sameElements(Array[Instruction](ACONST_NULL, ARETURN)))
  }
}
