package Task4
import Util.ComparableObject
import org.opalj.br.analyses.Project
import org.opalj.br.instructions.{INVOKEINTERFACE, INVOKESPECIAL, INVOKESTATIC, INVOKEVIRTUAL, Instruction}
import org.opalj.br.{ClassFile, FieldType, FieldTypes, MethodDescriptor, ObjectType}
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

class ModifyClassesTests extends AnyFunSuite{

  val obj1: ComparableObject = new ComparableObject("SpecialMethods <init> (Ljava/lang/String,int;)V")

  val searchFor: ListBuffer[ComparableObject] = ListBuffer[ComparableObject](obj1)
  val foundCallers: ListBuffer[ComparableObject] = ListBuffer[ComparableObject](obj1)
  var cf: ClassFile = _

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
    val mc = new ModifyClasses(null, searchFor, "Test", "Test")
    cf = Project(new java.io.File("src/test/scala/Task4/Test.jar")).allProjectClassFiles.head
    // in searched method - INVOKESPECIAL
    assert(!mc.testForSearchedMethod(special1, cf).equals(special1))
    assert(mc.testForSearchedMethod(special1, cf).equals(
      INVOKESPECIAL.apply(declaringClass = cf.thisType.fqn, isInterface = false, methodName = s"${cf.thisType.fqn.toLowerCase()}${special1.name}", methodDescriptor = s"${special1.methodDescriptor.toJVMDescriptor}")))
    // not in searched method - INVOKESPECIAL
    assert(mc.testForSearchedMethod(special2, null).equals(special2))

    // in searched method - INVOKEINTERFACE
    assert(!mc.testForSearchedMethod(interface1, cf).equals(interface1))
    assert(mc.testForSearchedMethod(interface1, cf).equals(
      INVOKEINTERFACE.apply(declaringClass = s"${cf.thisType.fqn}", methodName = s"${cf.thisType.fqn.toLowerCase()}${interface1.name}"
        , methodDescriptor = s"${interface1.methodDescriptor.toJVMDescriptor}")))
    // not in searched method - INVOKEINTERFACE
    assert(mc.testForSearchedMethod(interface2, null).equals(interface2))

    // in searched method - INVOKESTATIC
    assert(!mc.testForSearchedMethod(static1, cf).equals(static1))
    assert(mc.testForSearchedMethod(static1, cf).equals(
      INVOKESTATIC.apply(declaringClass = s"${cf.thisType.fqn}", isInterface = false, methodName = s"${cf.thisType.fqn.toLowerCase()}${static1.name}",
        methodDescriptor = s"${static1.methodDescriptor.toJVMDescriptor}")))
    // not in searched method - INVOKESTATIC
    assert(mc.testForSearchedMethod(static2, null).equals(static2))

    // in searched method - INVOKESPECIAL
    assert(!mc.testForSearchedMethod(virtual1, cf).equals(virtual1))
    assert(mc.testForSearchedMethod(virtual1, cf).equals(
      INVOKEVIRTUAL.apply(declaringClass = s"${cf.thisType.fqn}", methodName = s"${cf.thisType.fqn.toLowerCase()}${virtual1.name}", methodDescriptor = s"${virtual1.methodDescriptor.toJVMDescriptor}").asInstanceOf[Instruction]))
    // not in searched method - INVOKESPECIAL
    assert(mc.testForSearchedMethod(virtual2, null).equals(virtual2))
  }
}
