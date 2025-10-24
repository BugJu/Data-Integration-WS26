import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable


class ProjectManagerTest extends AnyFunSuite {

  val task6Obj: task6.type = task6

  /** [[projectManager]] */
  test("ProjectManager"){
    task6Obj.clear()
    task6Obj.projectFile = Array(new java.io.File("src\\test\\scala\\Analyse1\\application-1.jar"))
    task6Obj.libraryFile = Array(new java.io.File("src\\test\\scala\\Analyse1\\Lib\\utility-1.jar"))
    task6Obj.isLibrary = false
    // check json
    val pManager = new projectManager(task6Obj.projectFile, task6Obj.libraryFile, task6Obj.isLibrary)
    println("Help")
    pManager.printMapOfMaps()

    assert(!pManager.validate("org/example/utility/core", "org/example/utility/core"))

    assert(pManager.jarExists("application-1.jar"))
    assert(pManager.jarExists("utility-1.jar"))
  }
}
