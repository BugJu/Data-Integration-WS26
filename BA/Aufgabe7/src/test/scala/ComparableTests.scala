import Util.ComparableObject
import org.opalj.br.instructions.{INVOKEINTERFACE, INVOKESPECIAL}
import org.opalj.br.{FieldType, FieldTypes, MethodDescriptor, ObjectType}
import org.scalatest.funsuite.AnyFunSuite

class ComparableTests extends AnyFunSuite{

  val obj1: ComparableObject = new ComparableObject("SpecialMethods <init> (Ljava/lang/String,int;)V")
  val obj2: ComparableObject = new ComparableObject("SpecialMethods <init> (Ljava/lang/String,int;)I")

  val special1: INVOKESPECIAL = INVOKESPECIAL(declaringClass = ObjectType("SpecialMethods"), isInterface = false, name = "<init>",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))
  val special2: INVOKESPECIAL = INVOKESPECIAL(declaringClass = ObjectType("SpecialMethods"), isInterface = false, name = "getBo",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))

  val interface1: INVOKEINTERFACE = INVOKEINTERFACE(declaringClass = ObjectType("SpecialMethods"), name = "<init>",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))
  val interface2: INVOKEINTERFACE = INVOKEINTERFACE(declaringClass = ObjectType("SpecialMethods"), name = "getBo",
    methodDescriptor = MethodDescriptor(FieldTypes("Ljava/lang/String;,I;".split(",").map(FieldType.apply): _*), ObjectType("void")))

  val test: MethodDescriptor = MethodDescriptor("(Ljava/lang/String,int;)V")

  test("ComparableObjects.equals"){
    // ComparableObject with ComparableObject
    assert(obj1.equals(obj1))
    assert(!obj1.equals(obj2))
    // ComparableObject with INVOKESPECIAL
    assert(obj1.equals(special1))
    assert(!obj1.equals(special2))
    // ComparableObject with INVOKEINTERFACE
    assert(obj1.equals(interface1))
    assert(!obj1.equals(interface2))
  }

  test("MethodDescriptor is set as expected"){
    assert(obj1.checkMethodDescriptor.parameterTypes == test.parameterTypes)
  }
}
