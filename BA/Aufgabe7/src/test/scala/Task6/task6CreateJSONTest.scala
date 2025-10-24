import Task6.task6_1
import org.scalatest.funsuite.AnyFunSuite

class task6CreateJSONTest extends AnyFunSuite {

  val task6Obj: task6_1.type = task6_1

  /** [[task6.createJson()]] */
  test("task6.createJson - with valid file"){
    val file = new java.io.File("src/test/scala/Task6/Analyse1/Spezifikation.json")
    task6_1.createJson(file)
    assert(task6_1.jsonInput.defaultRule=="FORBIDDEN")
  }

  /** [[task6.createJson()]] */
  test("task6.createJson - with wrong file path"){
    val file = new java.io.File("src/test/scala/Task6/Analyse1/spezif.json")
    assertThrows[IllegalArgumentException] {
      task6_1.createJson(file)
    }
  }

  /** [[task6.createJson()]] */
  test("task6.createJson - with wrong json design"){
    val file = new java.io.File("src/test/scala/Task6/Example/Special.json")
    assertThrows[IllegalArgumentException] {
      task6_1.createJson(file)
    }
  }

}