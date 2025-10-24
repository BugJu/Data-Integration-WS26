import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable.ListBuffer
import scala.swing.Dimension


class CreateAnalysisTest extends AnyFunSuite{

  /** [[CreateAnalysis.createComboBox]] */
  test("OpenAnalysis.createComboBox"){
    val comboBox = CreateAnalysis.createComboBox(Array("Hallo", "Ich", "Bin", "Es").toSeq)
    val comboBoxElements = ListBuffer[String]()
    for(i <- 0 until comboBox.peer.getItemCount){
      comboBoxElements += comboBox.peer.getItemAt(i)
    }
    assert(comboBoxElements.equals(Array("Hallo", "Ich", "Bin", "Es").toSeq))
    assert(comboBox.preferredSize == new Dimension(1400, 30))
    assert(comboBox.minimumSize == new Dimension(1400, 30))
    assert(comboBox.maximumSize == new Dimension(1400, 30))
  }
}
