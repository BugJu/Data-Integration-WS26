import org.scalatest.funsuite.AnyFunSuite

class task6CreateJSONTest extends AnyFunSuite {

  val task6Obj: task6.type = task6

  /** [[task6.createJson()]] */
  test("task6.createJson - with valid file"){
    val file = new java.io.File("src/test/scala/Analyse1/Spezifikation.json")
    task6.createJson(file)
    assert(task6.jsonInput.defaultRule=="FORBIDDEN")
  }

  /** [[task6.createJson()]] */
  test("task6.createJson - with wrong file path"){
    val file = new java.io.File("src/test/scala/Analyse1/spezif.json")
    assertThrows[IllegalArgumentException] {
      task6.createJson(file)
    }
  }

  /** [[task6.createJson()]] */
  test("task6.createJson - with wrong json design"){
    val file = new java.io.File("src/test/scala/Example/Special.json")
    assertThrows[IllegalArgumentException] {
      task6.createJson(file)
    }
  }

}