
import org.opalj.ai.common.DomainRegistry

import java.io.File
import javax.swing.WindowConstants
import scala.collection.mutable.ListBuffer
import scala.swing.event.ButtonClicked
import scala.swing._

/** MainFrame which should always run. Analyses reports or create reports. */
object CreateAnalysis extends SimpleSwingApplication {

  /** MainFrame */
  def top: Frame = new MainFrame {
    title = "Abstract Interpretation"
    preferredSize = new Dimension(1500, 600)

    /** ComboBox with project names. */
    val comboBoxProject: ComboBox[String] = createComboBox(getProjects)
    /** ComboBox with domain names of currently chosen project in [[comboBoxProject]]. */
    val comboBoxDomain: ComboBox[String] = createComboBox(DomainRegistry.domainDescriptions.toSeq)

    /** Button that open an analysis for the json in the currently chosen project and domain in [[comboBoxProject]] and [[comboBoxDomain]] */
    val startAnalysis: Button = new Button("Start Analysis") {
      preferredSize = new Dimension(200, 30)
    }

    // button actions
    listenTo(startAnalysis)
    reactions += {
      case ButtonClicked(`startAnalysis`) =>
        new task5(comboBoxProject.selection.item, comboBoxDomain.selection.item)
        Dialog.showMessage(contents.head, "Analysis created.", title = "Successful", Dialog.Message.Info)
        //System.exit(0) // only important for benchmarks
    }

    /** Panel for MainFrame */
    val panel: BoxPanel = new BoxPanel(Orientation.Vertical) {
      // comboBoxes
      contents += comboBoxProject
      contents += Swing.VStrut(20) // Horizontal space
      contents += comboBoxDomain
      contents += Swing.VStrut(20) // Horizontal space
      // Buttons
      contents += startAnalysis
      border = Swing.EmptyBorder(10, 10, 10, 10) // spacing to borders of the frame
    }

    /** This is the used panel */
    val mainPanel: BorderPanel = new BorderPanel {
      layout(panel) = BorderPanel.Position.Center
    }

    // layout for mainframe
    contents = new BorderPanel {
      layout(mainPanel) = BorderPanel.Position.Center
      border = Swing.EmptyBorder(0, 0, 0, 0)
    }

    peer.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)

    centerOnScreen()
  }

  /**
   * Gets the names of all available projects.
   * @return names of found projects.
   */
  private def getProjects: Seq[String] = {
    val pathToDirectory = s"src//main//scala//Projects"
    val directory = new File(pathToDirectory)

    val arrayOfDirectories = ListBuffer[String]()

    // check if directory exist
    if (directory.exists && directory.isDirectory) {
      val filesAndDirs = directory.listFiles()
      filesAndDirs.foreach { file =>
        arrayOfDirectories += file.getName
      }
    } else {
      throw new IllegalArgumentException(s"${directory.getPath} is not a valid directory.")
    }
    arrayOfDirectories.toSeq
  }

  def createComboBox(content: Seq[String]): ComboBox[String] = {
    new ComboBox(content) {
      preferredSize = new Dimension(1400, 30)
      maximumSize = new Dimension(1400, 30)
      minimumSize = new Dimension(1400, 30)
    }
  }
}
