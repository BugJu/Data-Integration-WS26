import play.api.libs.json.Json

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.swing._
import scala.swing.event.SelectionChanged

class BenchmarkFrame(file: File) extends Frame {
  // Set up the frame properties
  this.title = file.getName
  preferredSize = new Dimension(1600, 1000)

  // ------------------ Output ------------------ //
  getOutput(file)

  // ------------------ ComboBox ------------------ //
  private val reports: Map[String, (DeadCodeReport, File)] = getReports(file.getPath)

  private val comboBoxReport: ComboBox[String] = new ComboBox[String](reports.keys.toList.sorted)

  private val reportToReportPanel: Map[String, BorderPanel] = getReportToReportPanel

  var content: BorderPanel = reportToReportPanel(comboBoxReport.selection.item)

  // ------------------ Panel ------------------ //
  /** Panel with contents. */
  val panelBasic: BoxPanel = new BoxPanel(Orientation.Vertical) {
    contents += comboBoxReport
    contents += content
    border = Swing.EmptyBorder(10, 10, 10, 10) // outer bounds
  }

  /** MainPanel - always holds current panel. */
  val mainPanel: BorderPanel = new BorderPanel {
    layout(panelBasic) = BorderPanel.Position.Center
  }

  // ------------------ other stuff ------------------
  // Add a listener to handle selection changes
  mainPanel.listenTo(comboBoxReport.selection)
  mainPanel.reactions += {
    case SelectionChanged(`comboBoxReport`) =>
      panelBasic.contents -= content
      content = reportToReportPanel(comboBoxReport.selection.item)
      panelBasic.contents += content
      mainPanel.revalidate()
      mainPanel.repaint()
  }


  // layout for mainframe
  contents = new BorderPanel {
    layout(mainPanel) = BorderPanel.Position.Center
    border = Swing.EmptyBorder(0, 0, 0, 0)
  }

  centerOnScreen()

  private def getReports(pathFile: String): Map[String, (DeadCodeReport, File)] = {
    val directory = new File(pathFile)

    // check if directory exist
    if (directory.exists && directory.isDirectory) {
      val files: Array[File] = directory.listFiles().filter(file => !file.getName.contains("Benchmark") && file.isFile && file.getName.contains(".json")).map( file => file)
      files.map( file => file.getName -> (Json.parse(new String(Files.readAllBytes(Paths.get(file.toURI)), StandardCharsets.UTF_8)).as[DeadCodeReport], file)).toMap
    } else {
      throw new IllegalArgumentException(s"${directory.getPath} is not a valid directory.")
    }
  }

  private def getReportToReportPanel: Map[String, BorderPanel] = {
    reports.map{ report =>
      report._1 -> new PanelTest(report._2._1).getPanel
    }
  }

  private def getOutput(file: File): Unit = {
    /** All report.json files */
    val files = file.listFiles().filter(file => file.getName.split('.').last == "json")

    /** All projectName of the files - alphabetically sorted */
    val projectNames = files
      .map(_.getName.split("_").head)
      .toSet
      .toList.sorted

    /** All domainNames of the files - alphabetically sorted */
    val domains = files
      .map(_.getName.split("_").init.last)
      .toSet
      .toList.sorted

    /** Contains files DeadCodeReport in right order */
    val output: Array[Array[DeadCodeReport]] = Array.fill(projectNames.length)(Array.fill(domains.length)(null))
    // output gets filled
    projectNames.zipWithIndex.flatMap{ case (project, ind) =>
      files.filter(file => file.getName.split("_").head == project).map{ file =>
        val index = domains.indexOf(file.getName.split("_").init.last)
        output(ind)(index) = Json.parse(new String(Files.readAllBytes(Paths.get(file.toURI)), StandardCharsets.UTF_8)).as[DeadCodeReport]
      }
    }

    /** Actual output */
    // ----- Runtime ----- //
    // for each project
    domains.foreach(ele => print(s"$ele,"))
    println()

    output.zipWithIndex.foreach{ case (_, ind) =>
      println(s"${projectNames(ind)}: ${output(ind).map(ele => ele.totalRuntimeMs).toList}")
    }

    println()

    // for each domain
    for(i <- domains.indices){
      println(domains(i))
      println(output.map(ele => ele(i).totalRuntimeMs).toList)
    }

    println("--------------------")

    // ----- Count instructions ----- //
    // for each project
    output.zipWithIndex.foreach{ case (_, ind) =>
      println(s"${projectNames(ind)}: ${output(ind).map(ele => calculateShare(ele.methodsFound)).toList}")
    }

    println()

    // for each domain
    for(i <- domains.indices){
      println(domains(i))
      println(output.map(ele => calculateShare(ele(i).methodsFound)).toList)
    }

    println("--------------------")

    // ----- Group instructions ----- //
    val filter: List[String] = List[String]("put", "get", "GOTO", "ALOAD", "ICONST", "RETURN",
      "POP", "THROW", "INVOKEVORTUAL", "INVOKESPECIAL", "INVOKESTATIC")
    // for each project
    output.zipWithIndex.foreach{ case (_, ind) =>
      println(s"${projectNames(ind)}:")
      println(filter)
      val groups = output(ind).map(ele => calculateGroups(ele.methodsFound, filter))
      groups.foreach(println(_))
    }
  }

  /** Groups instructions according to filter */
  def calculateGroups(methodsFound: List[MethodWithDeadCode], filter: List[String]): List[Any] = {
    getGroup(methodsFound.flatMap(method => method.deadInstructions.map{ instr => Array(instr.stringRepresentation, instr.programCounter)}), filter)
  }

  private def getGroup(instructions: List[Array[Any]], filter: List[String]): List[Any] = {
    val test = filter.map{ filt =>
      instructions.count( instr => instr.head.toString.split(" ").head.toLowerCase.contains( filt.toLowerCase ) )
    }
    test
  }

  /**
   * Calculates the share of dead instruction to total instructions.
   * @param methodsFound All methods analysed.
   * @return The share
   */
  def calculateShare(methodsFound: List[MethodWithDeadCode]): Double = {
    if(methodsFound.map(zw => zw.numberOfDeadInstructions ).sum != 0){
      val total = methodsFound.map(zw => zw.numberOfTotalInstructions ).sum
      val dead = methodsFound.map(zw => zw.numberOfDeadInstructions ).sum
      BigDecimal.decimal((dead / total.toFloat) * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
    }
    else{
      0
    }
  }

}