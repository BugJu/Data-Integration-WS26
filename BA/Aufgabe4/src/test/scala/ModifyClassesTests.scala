
import org.opalj.br.instructions.{INVOKEINTERFACE, INVOKESPECIAL, INVOKESTATIC, INVOKEVIRTUAL, Instruction}
import org.opalj.br.{FieldType, FieldTypes, MethodDescriptor, ObjectType}
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

class ModifyClassesTests extends AnyFunSuite{

  val obj1: ComparableObject = new ComparableObject("SpecialMethods <init> (Ljava/lang/String,int;)V")

  val searchFor: ListBuffer[ComparableObject] = ListBuffer[ComparableObject](obj1)
  val foundCallers: ListBuffer[ComparableObject] = ListBuffer[ComparableObject](obj1)

  val special1: INVOKESPECIAL = INVOKESPECIAL(declaringClass = ObjectType("SpecialMethods"), isInterface = false, name = "<init>",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))
  val special2: INVOKESPECIAL = INVOKESPECIAL(declaringClass = ObjectType("SpecialMethods"), isInterface = false, name = "getBo",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))

  val interface1: INVOKEINTERFACE = INVOKEINTERFACE(declaringClass = ObjectType("SpecialMethods"), name = "<init>",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))
  val interface2: INVOKEINTERFACE = INVOKEINTERFACE(declaringClass = ObjectType("SpecialMethods"), name = "getBo",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))

  val static1: INVOKESTATIC = INVOKESTATIC(declaringClass = ObjectType("SpecialMethods"), isInterface = false, name = "<init>",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))
  val static2: INVOKESTATIC = INVOKESTATIC(declaringClass = ObjectType("SpecialMethods"), isInterface = false, name = "getBo",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))

  val virtual1: INVOKEVIRTUAL = INVOKEVIRTUAL(declaringClass = ObjectType("SpecialMethods"), name = "<init>",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))
  val virtual2: INVOKEVIRTUAL = INVOKEVIRTUAL(declaringClass = ObjectType("SpecialMethods"), name = "getBo",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))

  test("ModifyClasses.testForSearchedMethod"){
    val mc = new ModifyClasses(null, searchFor)
    // in searched method - INVOKESPECIAL
    assert(!mc.testForSearchedMethod(special1, null).equals(special1))
    assert(mc.testForSearchedMethod(special1, null).equals(
      INVOKESPECIAL.apply(declaringClass = "NoOpClass", isInterface = false, methodName = "noOpMethod", methodDescriptor = "()V")))
    // not in searched method - INVOKESPECIAL
    assert(mc.testForSearchedMethod(special2, null).equals(special2))

    // in searched method - INVOKEINTERFACE
    assert(!mc.testForSearchedMethod(interface1, null).equals(interface1))
    assert(mc.testForSearchedMethod(interface1, null).equals(
      INVOKEINTERFACE.apply(declaringClass = "NoOpClass", methodName = "noOpMethod", methodDescriptor = "()V")))
    // not in searched method - INVOKEINTERFACE
    assert(mc.testForSearchedMethod(interface2, null).equals(interface2))

    // in searched method - INVOKESTATIC
    assert(!mc.testForSearchedMethod(static1, null).equals(static1))
    assert(mc.testForSearchedMethod(static1, null).equals(
      INVOKESTATIC.apply(declaringClass = "NoOpClass", isInterface = false, methodName = "noOpMethod", methodDescriptor = "()V")))
    // not in searched method - INVOKESTATIC
    assert(mc.testForSearchedMethod(static2, null).equals(static2))

    // in searched method - INVOKESPECIAL
    assert(!mc.testForSearchedMethod(virtual1, null).equals(virtual1))
    assert(mc.testForSearchedMethod(virtual1, null).equals(
      INVOKEVIRTUAL.apply(declaringClass = "NoOpClass", methodName = "noOpMethod", methodDescriptor = "()V").asInstanceOf[Instruction]))
    // not in searched method - INVOKESPECIAL
    assert(mc.testForSearchedMethod(virtual2, null).equals(virtual2))
  }
}
