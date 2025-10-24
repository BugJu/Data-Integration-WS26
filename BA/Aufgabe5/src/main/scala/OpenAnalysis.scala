import play.api.libs.json.Json

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{FileSystems, Files, Paths, WatchService}
import java.nio.file.StandardWatchEventKinds._
import javax.swing.WindowConstants
import javax.swing.filechooser.FileNameExtensionFilter
import scala.concurrent.Future
import scala.swing._
import scala.swing.event._
import scala.swing.FileChooser.Result._
import scala.concurrent.ExecutionContext.Implicits.global

/** MainFrame which should always run. Analyses reports or create reports. */
object OpenAnalysis extends SimpleSwingApplication {
  /** TextField for output information. */
  private var textArea: TextArea = _

  /** MainFrame */
  def top: Frame = new MainFrame {
    title = "Abstract Interpretation"
    preferredSize = new Dimension(1600, 1000)

    /** List of all available reports */
    var reports: Map[String, (DeadCodeReport, File)] = getReports
    /** ComboBox with project names. */
    val comboBoxReports: ComboBox[String] = createComboBox(reports.keys.toList.sorted)

    /** Labels */
    val labelFilesAnalyzed = new Label()
    val labelDomainUsed = new Label()
    if(reports.keys.nonEmpty){
      labelFilesAnalyzed.text = reports(comboBoxReports.selection.item)._1.filesAnalyzed.toString()
      labelDomainUsed.text = s"  ${reports(comboBoxReports.selection.item)._1.domainUsed}"
    }

    /** Button that open an analysis for the json in the currently chosen report in [[comboBoxReports]].*/
    val openAnalysis: Button = new Button("Open Analysis") {
      preferredSize = new Dimension(200, 30)
    }
    /** Opens an explorer to select a file manually. */
    val chooseAnalysis: Button = new Button("Choose Analysis") {
      preferredSize = new Dimension(200, 30)
    }
    // button actions
    listenTo(openAnalysis, chooseAnalysis)
    reactions += {
      case ButtonClicked(`openAnalysis`) =>
        val fileDeadCode = reports(comboBoxReports.selection.item)._1
        textArea.text += s"Opened report: ${reports(comboBoxReports.selection.item)._2.getName}\n"
        new Result(fileDeadCode)
      case ButtonClicked(`chooseAnalysis`) =>
        openFileExplorer(this)
    }

    /** Contains history */
    textArea = new TextArea(){
      minimumSize = new Dimension(2000, 100)
      editable = false
    }

    /** Panel for MainFrame */
    val panel: BorderPanel = new BorderPanel {
      // Oberer Teil mit den ComboBoxes und Buttons
      layout(new BoxPanel(Orientation.Vertical) {
        contents += comboBoxReports
        contents += new BorderPanel {
          layout(new BoxPanel(Orientation.Vertical) {
            contents += labelFilesAnalyzed
            contents += labelDomainUsed
          }) = BorderPanel.Position.Center
          border = Swing.EmptyBorder(10, 0, 10, 0)
        }
        contents += new BorderPanel {
          layout(new FlowPanel(FlowPanel.Alignment.Center)(
            openAnalysis,
            chooseAnalysis
          )) = BorderPanel.Position.Center
          border = Swing.EmptyBorder(10, 0, 10, 0)
        }
      }) = BorderPanel.Position.North // Alles in den oberen Bereich

      // TextArea im Restbereich
      layout(new ScrollPane(textArea)) = BorderPanel.Position.Center

      // Äußerer Rand
      border = Swing.EmptyBorder(10, 10, 10, 10)
    }

    // ----------- directory listener ----------
    // Erstelle einen WatchService
    val watchService: WatchService = FileSystems.getDefault.newWatchService()

    // Registriere das Verzeichnis beim WatchService
    Paths.get("src/main/scala/Results").register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)

    // Thread für die Überwachung des Verzeichnisses
    Future {
      while (true) {
        // Warten auf ein Ereignis
        val key = watchService.take()

        // Iteriere durch die Ereignisse
        key.pollEvents().forEach { event =>
          Thread.sleep(1000)
          event.kind() match {
            case ENTRY_CREATE =>
              None
            case ENTRY_MODIFY =>
              textArea.text += s"Datei geändert: ${event.context()}\n"
              reports = getReports
              comboBoxReports.peer.setModel(ComboBox.newConstantModel(reports.keys.toList.sorted))
              labelFilesAnalyzed.text = reports(comboBoxReports.selection.item)._1.filesAnalyzed.toString()
              labelDomainUsed.text = s"  ${reports(comboBoxReports.selection.item)._1.domainUsed}"
            case ENTRY_DELETE =>
              textArea.text += s"Datei gelöscht: ${event.context()}\n"
              reports = getReports
              comboBoxReports.peer.setModel(ComboBox.newConstantModel(reports.keys.toList.sorted))
              labelFilesAnalyzed.text = reports(comboBoxReports.selection.item)._1.filesAnalyzed.toString()
              labelDomainUsed.text = s"  ${reports(comboBoxReports.selection.item)._1.domainUsed}"
            case _ =>
          }
        }

        // Schlüssel zurücksetzen, um auf weitere Ereignisse zu warten
        val valid = key.reset()
        if (!valid) {
          println("WatchService wurde ungültig. Stoppe Überwachung.")
        }
      }
    }

    /** This is the used panel */
    val mainPanel: BorderPanel = new BorderPanel {
      layout(panel) = BorderPanel.Position.Center
    }

    // Add a listener to handle selection changes
    mainPanel.listenTo(comboBoxReports.selection)
    mainPanel.reactions += {
      case SelectionChanged(`comboBoxReports`) =>
        labelFilesAnalyzed.text = reports(comboBoxReports.selection.item)._1.filesAnalyzed.toString()
        labelDomainUsed.text = s"  ${reports(comboBoxReports.selection.item)._1.domainUsed}"
    }

    // layout for mainframe
    contents = new BorderPanel {
      layout(mainPanel) = BorderPanel.Position.Center
      border = Swing.EmptyBorder(0, 0, 0, 0)
    }

    // ---------- menubar ---------- //
    // Menüleiste hinzufügen
    menuBar = new MenuBar {
      contents += new Menu("Benchmark") {
        val listBenchmarks: List[File] = getBenchmarks
        listBenchmarks.foreach{ benchmark =>
          contents += new MenuItem(Action(benchmark.getName) {
            Swing.onEDT {
              val frame = new BenchmarkFrame(benchmark)
              frame.open()
            }
          })
        }
        private def getBenchmarks: List[File] = {
          val pathToDirectory = s"src//main//scala//Results"
          val directory = new File(pathToDirectory)
          // check if directory exist
          if (directory.exists && directory.isDirectory) {
            directory.listFiles().filter(directory => directory.getName.contains("Benchmark")).map(directory => directory).toList
          } else {
            throw new IllegalArgumentException(s"${directory.getPath} is not a valid directory.")
          }
        }
      }
    }

    peer.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)

    centerOnScreen()
  }

  /**
   * Opens a fileChooser to load a report.json.
   * @param frame MainFrame
   */
  private def openFileExplorer(frame: MainFrame): Unit = {
    val chooser = new FileChooser(new File("src//main/scala/Results")) // start directory (should be results folder)
    chooser.title = "Chose a report (.json)"
    chooser.fileSelectionMode = FileChooser.SelectionMode.FilesOnly // Only files allowed

    // file filter to only allow json
    chooser.fileFilter = new FileNameExtensionFilter("JSON-Files", "json")

    val result = chooser.showOpenDialog(frame.contents.head) // show dialog
    result match {
      case Approve =>
        val selectedFile = chooser.selectedFile
        if (selectedFile.getName.endsWith(".json")) {
          textArea.text += s"Opened report: $selectedFile\n"
          new Result(Json.parse(new String(Files.readAllBytes(Paths.get(selectedFile.toURI)), StandardCharsets.UTF_8)).as[DeadCodeReport])
        }
        else {
          Dialog.showMessage(frame.contents.head, "Please chose a valid json.", title = "Invalid json", Dialog.Message.Error)
        }
      case _ =>
    }
  }

  /**
   * Gets the names of all available reports.
   * @return Json of all found reports as [[DeadCodeReport]].
   */
  private def getReports: Map[String, (DeadCodeReport, File)] = {
    val pathToDirectory = s"src/main/scala/Results"
    val directory = new File(pathToDirectory)

    // check if directory exist
    if (directory.exists && directory.isDirectory) {
      val files: Array[File] = directory.listFiles().filter(file => !file.getName.contains("Benchmark") && file.isFile && file.getName.contains(".json")).map( file => file)
      files.map( file => file.getName -> (Json.parse(new String(Files.readAllBytes(Paths.get(file.toURI)), StandardCharsets.UTF_8)).as[DeadCodeReport], file)).toMap
    } else {
      throw new IllegalArgumentException(s"${directory.getPath} is not a valid directory.")
    }
  }

  /**
   * Creates a comboBox with the given content.
   * @param content Given content
   * @return Returns the combobox with the preferredSize of (200, 30)
   */
  def createComboBox(content: Seq[String]): ComboBox[String] = {
    new ComboBox(content) {preferredSize = new Dimension(200, 30)}
  }
}
