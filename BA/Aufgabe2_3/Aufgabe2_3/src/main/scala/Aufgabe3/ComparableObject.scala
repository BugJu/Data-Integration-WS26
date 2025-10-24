package Aufgabe3

import org.opalj.br.{FieldTypes, ObjectType}

/**
 * Saves important information for each method to make them comparable.
 * @param checkClass The class where the method is.
 * @param checkMethodName The method which is used.
 * @param checkMethodParameter The parameter of the method used.
 */
case class ComparableObject(checkClass: Int, checkMethodName: String, checkMethodParameter: FieldTypes) {

  override def toString: String = {
    var output = s"Klassen-ID: $checkClass\nMethod: $checkMethodName\n"
    checkMethodParameter.foreach{ param =>
      output += s"Parameter: $param\n"
    }
    output.substring(0, output.length-1)
  }

  // Überschreiben von `equals` und `hashCode`, um doppelte Objekte zu vermeiden
  override def equals(obj: Any): Boolean = obj match {
    case that: ComparableObject =>
      this.checkClass == that.checkClass &&
        this.checkMethodName == that.checkMethodName &&
        this.checkMethodParameter == that.checkMethodParameter
    case _ => false
  }

  override def hashCode(): Int = {
    // Berechnung des Hash-Codes auf der Basis aller Felder
    41 * (41 * (41 + checkClass.hashCode) + checkMethodName.hashCode) + checkMethodParameter.hashCode
  }
}
