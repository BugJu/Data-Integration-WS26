import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable.ListBuffer
import scala.io.Source

class task6WriteAndOutputTest extends AnyFunSuite {

  val task6Obj: task6.type = task6

  /** [[task6.readInput()]] */
  test("task6.writeAndOutput - with valid inputs"){
    task6.projectFile = Array(new java.io.File("src/test/scala/Analyse1/application-1.jar"))
    val input = ListBuffer("Moin", "Meister")
    task6.writeAndOutput(input)

    val source = Source.fromFile("src/test/scala/Analyse1/Result.txt")
    var lines: List[String] = List()
    try {
      lines = source.getLines().toList // Liest die Datei Zeile für Zeile als Liste
    } finally {
      source.close() // Ressource freigeben
    }
    assert(input.toList == lines)
  }
}
