
import java.awt.Color
import scala.swing.event.SelectionChanged
import scala.swing.{Alignment, BorderPanel, BoxPanel, ComboBox, Dimension, Graphics2D, Label, Orientation, Panel, ScrollPane, Swing, Table}

class PanelTest(deadCodeReport: DeadCodeReport){

  /** Report which gets analysed. */
  private var report: DeadCodeReport = _
  /** comboBoxClasses */
  private val comboBoxClasses: ComboBox[String] = new ComboBox[String](Seq("All"))
  /** comboBoxMethods */
  private val comboBoxMethods: ComboBox[String] = new ComboBox[String](Seq("All"))

  /** Filter for the instructions group */
  private val filter: List[String] = List[String]("put", "get", "GOTO", "ALOAD", "ICONST", "RETURN",
    "POP", "THROW", "INVOKEVORTUAL", "INVOKESPECIAL", "INVOKESTATIC", "Sonstige")

  report = deadCodeReport

  def getPanel: BorderPanel = {
    // ------------------ Maps ------------------
    // map class to method
    val classNames: List[String] = report.methodsFound.map(method => method.enclosingTypeName).distinct
    // map method to instruction
    val methodNames: List[String] = report.methodsFound.map(method => method.fullSignature)

    // ------------------ comboBox classes ------------------ //
    /** ComboBox with 'all' and all possible classes. */
    comboBoxClasses.peer.setModel(ComboBox.newConstantModel("All" :: classNames))

    // ------------------ comboBox methods ------------------ //
    /** Map[className, 'all'::methodNames] */
    val classNameToMethodNames: Map[String, List[String]] = ("All" :: classNames).map { className =>
      // for 'all'
      if (className == "All") {
        className -> ("All" :: methodNames)
      }
      // for actual classNames
      else {
        className -> ("All" :: report.methodsFound.filter(_.enclosingTypeName == className).map { method => method.fullSignature })
      }
    }.toMap

    /** ComboBox with 'all' and all possible methods for the chosen class in [[comboBoxClasses]]. */
    comboBoxMethods.peer.setModel(ComboBox.newConstantModel(classNameToMethodNames(comboBoxClasses.selection.item)))

    // ------------------ rectangle ------------------ //
    /** Map[(className, methodName), BorderPanel ] - contains rectangle, total instruction number, dead instruction number and percentage of total to dead instructions. */
    val rectangle: Map[(String, String), BoxPanel] = getRectangleMap("All" :: classNames, classNameToMethodNames)
    /** Panel for the rectangle - needs to be changeable. */
    var rec: BoxPanel = rectangle(comboBoxClasses.selection.item, comboBoxMethods.selection.item)

    // ------------------ table - dead instructions ------------------ //
    /** Map[(className, methodName), ScrollPane] - contains the table for dead instructions */
    val table: Map[(String, String), ScrollPane] = getTableMap("All" :: classNames, classNameToMethodNames)
    /** Panel for the table - needs to be changeable. */
    var tab: ScrollPane = table(comboBoxClasses.selection.item, comboBoxMethods.selection.item)

    // ------------------ table - instructions groups ------------------ //
    /** Map[(className, methodName), ScrollPane] - contains the table for instruction groups */
    val table2: Map[(String, String), ScrollPane] = getTableGroupMap("All" :: classNames, classNameToMethodNames)
    /** Panel for the table with instructions group - needs to be changeable. */
    var tab2: ScrollPane = table2(comboBoxClasses.selection.item, comboBoxMethods.selection.item)

    // ------------------ Panel ------------------ //
    /** Panel with contents. */
    val panelBasic: BoxPanel = new BoxPanel(Orientation.Vertical) {
      contents += getProjectInformationPanel
      contents += new Label("Classes")
      contents += comboBoxClasses
      contents += Swing.VStrut(20)
      contents += new Label("Methods")
      contents += comboBoxMethods
      contents += Swing.VStrut(20)
      contents += rec
      contents += Swing.VStrut(20)
      contents += tab
      contents += Swing.VStrut(20)
      contents += tab2
      border = Swing.EmptyBorder(10, 10, 10, 10) // outer bounds
    }

    /** MainPanel - always holds current panel. */
    val mainPanel: BorderPanel = new BorderPanel {
      layout(panelBasic) = BorderPanel.Position.Center
    }

    /** Listener to change [[comboBoxMethods]] to match the selected item in [[comboBoxClasses]] again. */
    mainPanel.listenTo(comboBoxClasses.selection, comboBoxMethods.selection)
    mainPanel.reactions += {
      case SelectionChanged(`comboBoxClasses`) =>
        comboBoxMethods.peer.setModel(ComboBox.newConstantModel(classNameToMethodNames(comboBoxClasses.selection.item)))
        panelBasic.contents -= rec
        panelBasic.contents -= tab
        panelBasic.contents -= tab2
        panelBasic.contents.remove(panelBasic.contents.length-1)
        panelBasic.contents.remove(panelBasic.contents.length-1)
        rec = rectangle(comboBoxClasses.selection.item, comboBoxMethods.selection.item)
        tab = table(comboBoxClasses.selection.item, comboBoxMethods.selection.item)
        tab2 = table2(comboBoxClasses.selection.item, comboBoxMethods.selection.item)
        panelBasic.contents += rec
        panelBasic.contents += Swing.VStrut(20)
        panelBasic.contents += tab
        panelBasic.contents += Swing.VStrut(20)
        panelBasic.contents += tab2
        mainPanel.revalidate()
        mainPanel.repaint()
      case SelectionChanged(`comboBoxMethods`) =>
        panelBasic.contents -= rec
        panelBasic.contents -= tab
        panelBasic.contents -= tab2
        panelBasic.contents.remove(panelBasic.contents.length-1)
        panelBasic.contents.remove(panelBasic.contents.length-1)
        rec = rectangle(comboBoxClasses.selection.item, comboBoxMethods.selection.item)
        tab = table(comboBoxClasses.selection.item, comboBoxMethods.selection.item)
        tab2 = table2(comboBoxClasses.selection.item, comboBoxMethods.selection.item)
        panelBasic.contents += rec
        panelBasic.contents += Swing.VStrut(20)
        panelBasic.contents += tab
        panelBasic.contents += Swing.VStrut(20)
        panelBasic.contents += tab2
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    mainPanel
  }

  private def getTableGroupMap(classNames: List[String], classNameToMethodNames: Map[String, List[String]]): Map[(String, String), ScrollPane] = {
    ("All"::classNames).flatMap{ className =>
      // for className = 'all'
      if(className == "All"){
        classNameToMethodNames(className).map{ methodName =>
          // for methodName == 'all'
          if(methodName == "All"){
            val instructions = report.methodsFound.flatMap(method => method.deadInstructions.map { instr =>
              Array(method.enclosingTypeName, method.fullSignature, instr.stringRepresentation, instr.programCounter)
            }).toArray
            (className, methodName) -> new ScrollPane(getTable(getGroup(instructions), filter.toArray))
          }
          // for actual methodNames
          else{
            val instructions = report.methodsFound.filter(method => method.fullSignature == methodName).flatMap(method => method.deadInstructions.map{ instr =>
              Array(method.enclosingTypeName, method.fullSignature, instr.stringRepresentation, instr.programCounter)
            }).toArray
            (className, methodName) -> new ScrollPane(getTable(getGroup(instructions), filter.toArray))
          }
        }
      }
      // for actual classNames
      else{
        classNameToMethodNames(className).map{ methodName =>
          // for methodName == 'all'
          if(methodName == "All"){
            val instructions = report.methodsFound.filter(method => method.enclosingTypeName == className).flatMap(method => method.deadInstructions.map{ instr => Array(method.enclosingTypeName, method.fullSignature, instr.stringRepresentation, instr.programCounter)}).toArray
            (className, methodName) -> new ScrollPane(getTable(getGroup(instructions), filter.toArray))
          }
          // for actual methodNames
          else{
            val instructions = report.methodsFound.filter(method => method.enclosingTypeName == className && method.fullSignature == methodName).flatMap(method => method.deadInstructions).map{ instr => Array(className, methodName, instr.stringRepresentation, instr.programCounter)}.toArray
            (className, methodName) -> new ScrollPane(getTable(getGroup(instructions), filter.toArray))
          }
        }
      }
    }.toMap
  }

  private def getGroup(instructions: Array[Array[Any]]): Array[Array[Any]] = {
    val test = filter.map{ filt =>
      instructions.count( instr => instr(2).toString.split(" ").head.contains( filt ) )
    }
    Array((test.init :+ (instructions.length-test.init.map{ele => ele}.sum)).toArray)
  }

  /**
   * Creates a rectangle for every possible combination of classNames and methodNames.
   *
   * @param classNames ClassNames
   * @param classNameToMethodNames Map[className, methodNames]
   * @return
   */
  private def getRectangleMap(classNames: List[String], classNameToMethodNames: Map[String, List[String]]): Map[(String, String), BoxPanel] = {
    ("All"::classNames).flatMap{ className =>
      // for className = 'all'
      if(className == "All"){
        classNameToMethodNames(className).map{ methodName =>
          // for methodName == 'all'
          if(methodName == "All"){
            val listOfInstructionsCount: List[(Int, Int)] = report.methodsFound.map( method => (method.numberOfTotalInstructions, method.numberOfDeadInstructions))
            (className, methodName) -> getDeadCodeAmountAllPanel(listOfInstructionsCount.map( int => int._1).sum, listOfInstructionsCount.map( int => int._2).sum)
          }
          // for actual methodNames
          else{
            val listOfInstructionsCount: List[(Int, Int)] = report.methodsFound.filter(method => method.fullSignature == methodName).map( method => (method.numberOfTotalInstructions, method.numberOfDeadInstructions))
            (className, methodName) -> getDeadCodeAmountAllPanel(listOfInstructionsCount.map( int => int._1).sum, listOfInstructionsCount.map( int => int._2).sum)
          }
        }
      }
      // for actual classNames
      else{
        classNameToMethodNames(className).map{ methodName =>
          // for methodName == 'all'
          if(methodName == "All"){
            val listOfInstructionsCount: List[(Int, Int)] = report.methodsFound.filter(method => method.enclosingTypeName == className).map( method => (method.numberOfTotalInstructions, method.numberOfDeadInstructions))
            (className, methodName) -> getDeadCodeAmountAllPanel(listOfInstructionsCount.map( int => int._1).sum, listOfInstructionsCount.map( int => int._2).sum)
          }
          // for actual methodNames
          else{
            val listOfInstructionsCount: List[(Int, Int)] = report.methodsFound.filter(method => method.enclosingTypeName == className && method.fullSignature == methodName).map( method => (method.numberOfTotalInstructions, method.numberOfDeadInstructions))
            (className, methodName) -> getDeadCodeAmountAllPanel(listOfInstructionsCount.map( int => int._1).sum, listOfInstructionsCount.map( int => int._2).sum)
          }
        }
      }
    }.toMap
  }

  /**
   * Creates a table for every possible combination of classNames and methodNames.
   * @param classNames ClassNames
   * @param classNameToMethodNames Map[className, methodNames]
   * @return
   */
  private def getTableMap(classNames: List[String], classNameToMethodNames: Map[String, List[String]]): Map[(String, String), ScrollPane] = {
    ("All"::classNames).flatMap{ className =>
      // for className = 'all'
      if(className == "All"){
        classNameToMethodNames(className).map{ methodName =>
          // for methodName == 'all'
          if(methodName == "All"){
            val instructions = report.methodsFound.flatMap(method => method.deadInstructions.map { instr =>
              Array(method.enclosingTypeName, method.fullSignature, instr.stringRepresentation, instr.programCounter)
            }).toArray
            (className, methodName) -> new ScrollPane(getTable(instructions, Array("Class", "Method", "Instruction", "PC")))
          }
          // for actual methodNames
          else{
            val instructions = report.methodsFound.filter(method => method.fullSignature == methodName).flatMap(method => method.deadInstructions.map{ instr =>
              Array(method.enclosingTypeName, method.fullSignature, instr.stringRepresentation, instr.programCounter)
            }).toArray
            (className, methodName) -> new ScrollPane(getTable(instructions, Array("Class", "Method", "Instruction", "PC")))
          }
        }
      }
      // for actual classNames
      else{
        classNameToMethodNames(className).map{ methodName =>
          // for methodName == 'all'
          if(methodName == "All"){
            val instructions = report.methodsFound.filter(method => method.enclosingTypeName == className).flatMap(method => method.deadInstructions.map{ instr => Array(method.enclosingTypeName, method.fullSignature, instr.stringRepresentation, instr.programCounter)}).toArray
            (className, methodName) -> new ScrollPane(getTable(instructions, Array("Class", "Method", "Instruction", "PC")))
          }
          // for actual methodNames
          else{
            val instructions = report.methodsFound.filter(method => method.enclosingTypeName == className && method.fullSignature == methodName).flatMap(method => method.deadInstructions).map{ instr => Array(className, methodName, instr.stringRepresentation, instr.programCounter)}.toArray
            (className, methodName) -> new ScrollPane(getTable(instructions, Array("Class", "Method", "Instruction", "PC")))
          }
        }
      }
    }.toMap
  }

  /**
   * Contains the amount of dead code of all methods.
   * @param instructionsShareAll The calculated amount of all (total instructions, unused instructions)
   * @return BorderPanel that contains information the amount if deadCode.
   */
  private def getDeadCodeAmountAllPanel(instructionsShareAll: (Int, Int)): BoxPanel = {
    new BoxPanel(Orientation.Horizontal) {
      contents += new Panel {
        preferredSize = new Dimension(300, 50)
        maximumSize = new Dimension(300, 50)
        override def paintComponent(g: Graphics2D): Unit = {
          super.paintComponent(g)
          val width = 200
          val height = 50
          var redWidth = 0
          if(instructionsShareAll._1.toFloat == 0){
            redWidth = width * 0
          }
          else{
            redWidth = (width * BigDecimal.decimal((instructionsShareAll._2 / instructionsShareAll._1.toFloat) * 100)
              .setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble / 100.0).toInt
          }
          val greenWidth = width - redWidth

          // draw red
          g.setColor(Color.RED)
          g.fillRect(0, 0, redWidth, height)

          // draw green
          g.setColor(Color.GREEN)
          g.fillRect(redWidth, 0, greenWidth, height)
        }
      }
      // spacing
      contents += Swing.HStrut(20)
      // add labels
      contents += new Label(s"Total-Instruction: ${instructionsShareAll._1}"){
        preferredSize = new Dimension(150, 50)
        maximumSize = new Dimension(150, 50)
        horizontalAlignment = Alignment.Left
      }
      contents += Swing.HStrut(20)

      contents += new Label(s"Unused-Instruction: ${instructionsShareAll._2}") {
        preferredSize = new Dimension(150, 50)
        maximumSize = new Dimension(150, 50)
        horizontalAlignment = Alignment.Left
      }
      contents += Swing.HStrut(20)
      if(instructionsShareAll._1.toFloat == 0){
        contents += new Label(s"Percentage: ${BigDecimal(0*100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble}%") {
          preferredSize = new Dimension(150, 50)
          maximumSize = new Dimension(150, 50)
          horizontalAlignment = Alignment.Left
        }
      }
      else{contents += new Label(s"Percentage: ${BigDecimal.decimal((instructionsShareAll._2/instructionsShareAll._1.toFloat)*100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble}%") {
        preferredSize = new Dimension(150, 50)
        maximumSize = new Dimension(150, 50)
        horizontalAlignment = Alignment.Left
        }
      }
    }
  }

  /**
   * Creates a table.
   * @param instructions Data for table
   * @param columnNames columnNames.
   * @return Table
   */
  private def getTable(instructions :Array[Array[Any]], columnNames: Array[String]): Table = {
    new Table(instructions , columnNames) {
      preferredViewportSize = new Dimension(400, 300)
      rowHeight = 25
      // deactivate cellEditor for whole table
      peer.setDefaultEditor(classOf[Any], null)
      // deactivate cellSelection
      peer.setCellSelectionEnabled(false)
      /** Reaction to click */
      if(columnNames.contains("Method")){
        // Reagiere auf Mausklicks
        peer.addMouseListener(new java.awt.event.MouseAdapter {
          override def mouseClicked(e: java.awt.event.MouseEvent): Unit = {
            val row = peer.rowAtPoint(e.getPoint)
            val col = peer.columnAtPoint(e.getPoint)

            if (row != -1 && col != -1) {
              val cellContent = instructions(row)(col)
              println(s"Ausgewählte Zelle: [$row, $col] -> Inhalt: $cellContent")
              // class
              if(col == 0){
                comboBoxClasses.peer.getModel.setSelectedItem(cellContent)
              }
              // method
              else if(col == 1){
                comboBoxClasses.peer.getModel.setSelectedItem(instructions(row)(0))
                comboBoxMethods.peer.getModel.setSelectedItem(cellContent)
              }
            }
          }
        })
      }
      else{
        peer.addMouseListener(new java.awt.event.MouseAdapter {
          override def mouseClicked(e: java.awt.event.MouseEvent): Unit = {
            val row = peer.rowAtPoint(e.getPoint)
            val col = peer.columnAtPoint(e.getPoint)

            if (row != -1 && col != -1) {
              val cellContent = instructions(row)(col)
              println(s"Ausgewählte Zelle: [$row, $col] -> Inhalt: $cellContent")
              // class
              println(columnNames(col))

            }
          }
        })
      }

    }
  }

  /**
   * Contains the basic report information (Like projectName, usedDomainName, dateCreated, runtime). [[DeadCodeReport]]
   * @return BorderPanel that contains information about the report.
   */
  private def getProjectInformationPanel: BorderPanel = {
    new BorderPanel {
      layout(new BoxPanel(Orientation.Vertical) {
        contents += new Label(s"Project: ${report.filesAnalyzed}")
        contents += new Label(s"Domain: ${report.domainUsed.split('.').last.replace("]", ":")}")
        contents += new Label(s"Created: ${report.timeFinished}")
        contents += new Label(s"Duration of calculation: ${report.totalRuntimeMs}Ms")

        // modify labels
        contents.foreach {
          case label: Label =>
            label.horizontalAlignment = Alignment.Left
            label.border = Swing.EmptyBorder(5, 10, 5, 0)
          case _ =>
        }
      }) = BorderPanel.Position.West
    }
  }

}
