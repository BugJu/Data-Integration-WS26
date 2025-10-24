import org.scalatest.funsuite.AnyFunSuite

class task6ReadInputTest extends AnyFunSuite {

  val task6Obj: task6.type = task6

  /** [[task6.readInput()]] */
  test("task6.readInput - with valid inputs"){
    val args = Array("filePath=src/test/scala/Analyse1", "isLibrary=false")
    task6.readInput(args.head, args(1))
    assert(!task6.isLibrary)
    assert(task6.projectFile.head.getPath=="src\\test\\scala\\Analyse1\\application-1.jar")
    assert(task6.libraryFile.map(lib => lib.getPath).toList == List("src\\test\\scala\\Analyse1\\Lib\\utility-1.jar"))
  }

  /** [[task6.readInput()]] */
  test("task6.readInput - wrong filePath given"){
    val args = Array("fil=src/test/scala/Analyse1", "isLibrary=false")
    assertThrows[IllegalArgumentException] {
      task6.readInput(args.head, args(1))
    }
  }

  /** [[task6.readInput()]] */
  test("task6.readInput - filePath is not a directory"){
    val args = Array("filePath=src\\test\\scala\\Analyse1\\application-1.jar", "isLibrary=false")
    assertThrows[IllegalArgumentException] {
      task6.readInput(args.head, args(1))
    }
  }

  /** [[task6.readInput()]] */
  test("task6.readInput - multiple json files"){
    val args = Array("filePath=src/test/scala/Example2", "isLibrary=false")
    assertThrows[IllegalArgumentException] {
      task6.readInput(args.head, args(1))
    }
  }

  /** [[task6.readInput()]] */
  test("task6.readInput - multiple lib directories"){
    val args = Array("filePath=src/test/scala/Example3", "isLibrary=false")
    assertThrows[IllegalArgumentException] {
      task6.readInput(args.head, args(1))
    }
  }
}
