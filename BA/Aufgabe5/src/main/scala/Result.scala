

import javax.swing.WindowConstants
import scala.swing._

/** Used to analyse a report.json. */
class Result {

  /** Path to the report which gets analysed. */
  private var deadCodeReport: DeadCodeReport = _

  /** Constructor for this class. Open a frame [[top]] and set's the [[deadCodeReport]].  */
  def this(deadCodeReport: DeadCodeReport) = {
    this()
    this.deadCodeReport = deadCodeReport
    top
  }

  /** Frame for analysis. */
  private def top: Frame = new Frame {
    title = "Analysis"

      // ------------------ other stuff ------------------
      // layout for mainframe
      contents = new BorderPanel {
        layout(new PanelTest(deadCodeReport).getPanel) = BorderPanel.Position.Center
        border = Swing.EmptyBorder(0, 0, 0, 0)
      }
      size = new Dimension(1600, 1000)
      peer.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
      // center window
      centerOnScreen()
      open()
    }
}