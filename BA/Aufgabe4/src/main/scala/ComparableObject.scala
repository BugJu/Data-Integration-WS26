import org.opalj.br.instructions.{InvocationInstruction, NonVirtualMethodInvocationInstruction, VirtualMethodInvocationInstruction}
import org.opalj.br._

/**
 * Saves important information for each method to make them comparable.
 */
class ComparableObject{

  /**  Stores class*/
  var checkClass: ObjectType = _
  /**  Stores method name*/
  var checkMethodName: String = _
  /**  Stores method descriptor*/
  var checkMethodDescriptor: MethodDescriptor = _

  /** Stores classFile - only used for 4_2 */
  var storeClassFile: ClassFile = _
  /** Stores method - only used for 4_2 */
  var storeMethod: Method = _

  /** Creates [[ComparableObject]] for searched methods.
   * @param input From file.txt - java/lang/System setSecurityManager Ljava/lang/SecurityManager; void */
  def this(input: String) = {
    this()
    val array = input.split(" ")
    this.checkClass = ObjectType(array.head)
    this.checkMethodName = array(1)
    this.checkMethodDescriptor = MethodDescriptor(array(2))
    //println(checkClass + " " + checkMethodName + " " + checkMethodDescriptor) // only for tests
  }

  /** Creates [[ComparableObject]] for found methods that call at least on searched method.
   * @param method method that is a caller.  */
  def this(method: DeclaredMethod) = {
    this()
    this.checkClass = method.declaringClassType
    this.checkMethodName = method.name
    this.checkMethodDescriptor = method.descriptor
  }

  /** Creates [[ComparableObject]] for library combinations of classFiles and methods.
   * @param classFile class
   * @param name Name of method
   * @param methodDescriptor Descriptor of method.
   * @param cf ClassFile as ClassFile
   * @param method Method
   */
  def this(classFile: ObjectType, name: String, methodDescriptor: MethodDescriptor, cf: ClassFile, method: Method) = {
    this()
    this.checkClass = classFile
    this.checkMethodName = name
    this.checkMethodDescriptor = methodDescriptor
    this.storeClassFile = cf
    this.storeMethod = method
  }

  /** Basic String representation. */
  override def toString: String = {
    var output = s"Klassen-ID: $checkClass\nMethod: $checkMethodName\n"
    output += s"Descriptor: $checkMethodDescriptor\n"
    output.substring(0, output.length-1)
  }

  /** Tests for equality.
   * @param obj can be: ComparableObject, Method, NonVirtualMethodInvocationInstruction,
   * VirtualMethodInvocationInstruction, InvocationInstruction */
  override def equals(obj: Any): Boolean = obj match {
    case that: ComparableObject =>
      this.checkClass == that.checkClass &&
        this.checkMethodName == that.checkMethodName &&
        this.checkMethodDescriptor == that.checkMethodDescriptor
    case that: Method =>
      this.checkClass == that.classFile.thisType &&
        this.checkMethodName == that.name &&
        this.checkMethodDescriptor.toJava == that.descriptor.toJava
  }

  def equals(that: Method): Boolean = {
    helperEquals(that.classFile.thisType,  that.name, that.descriptor.toJava)
  }

  def equals(that: DeclaredMethod): Boolean = {
    helperEquals(that.declaringClassType,  that.name, that.descriptor.toJava)
  }

  def equals(that: InvocationInstruction): Boolean = that match {
    case that: NonVirtualMethodInvocationInstruction =>
      that.name == this.checkMethodName &&
        that.declaringClass == this.checkClass &&
        that.methodDescriptor.toJava == this.checkMethodDescriptor.toJava
    case that: VirtualMethodInvocationInstruction =>
      that.name == this.checkMethodName &&
        that.declaringClass == this.checkClass &&
        that.methodDescriptor.toJava == this.checkMethodDescriptor.toJava
    // not rly useful but whatever
    case that:  InvocationInstruction =>
      that.name == this.checkMethodName &&
        that.methodDescriptor.toJava == this.checkMethodDescriptor.toJava
    case _ => false
  }

  private def helperEquals(dCT: ObjectType, n: String, d: String): Boolean = {
    this.checkClass == dCT &&
    this.checkMethodName == n &&
    this.checkMethodDescriptor.toJava == d
  }

  override def hashCode(): Int = {
    // Berechnung des Hash-Codes auf der Basis aller Felder
    41 * (41 * (41 + checkClass.hashCode) + checkMethodName.hashCode) + checkMethodDescriptor.hashCode
  }
}
