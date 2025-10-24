import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

class AnalyseTest extends AnyFunSuite{


  /** [[Analysis]] */
  test("Analyse - with valid inputs"){
    task6.main(Array("filePath=src/test/scala/Analyse1", "isLibrary=false"))
  }
}
