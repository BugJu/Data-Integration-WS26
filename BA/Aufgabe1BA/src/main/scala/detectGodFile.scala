import org.opalj.br.analyses.Project

import scala.io.StdIn.readLine
import java.io.File
import scala.collection.mutable.ListBuffer;


object detectGodFile {
  def main(args: Array[String]): Unit = {
    val file1 = new java.io.File("Test.jar")
    val file2 = new java.io.File("pdfbox-2.0.24.jar")
    println("Please select which project you want to analyze, default is 1:")
    val project = Project(if (readLine() == "1") file1 else file2)
    var setMethodLimit = 3 // 40
    var getMethodLimit = 0
    var setFieldLimit = 15 // 15
    var getFieldLimit = 0
    var setInstructionLimit = 500 // 500
    var getInstructionLimit = 0
    var setMaxCohesion = 0.8 // 0.8
    var setMaxCoupling = 5 // 5
    var setMaxPropertiesFalse = 4 // 4
    var classList = ListBuffer[Map[String, Any]]()


    project.allProjectClassFiles.foreach { cf =>
      cf.methodsWithBody.foreach { m =>
        if (m.body.isDefined) {
          getMethodLimit += 1
          getInstructionLimit = m.body.get.instructionsCount
        }
      }
      cf.fields.foreach { _ =>
        getFieldLimit += 1
      }
      cf.depe

      val newMap = Map("classFile" -> cf.fqn, "methods" -> getMethodLimit, "fields" -> getFieldLimit,"instructions" -> getInstructionLimit)
      classList = newMap +: classList
      getMethodLimit = 0
      getFieldLimit = 0

    }
    println(classList)
  }
}
