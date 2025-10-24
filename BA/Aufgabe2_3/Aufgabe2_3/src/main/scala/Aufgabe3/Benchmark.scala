package Aufgabe3

import org.opalj.control.repeat

import java.io.{BufferedReader, FileReader, FileWriter, PrintWriter}
import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

// file=commons-collections4-4.5.0-M2.jar dp=commons-collection cg=CHA il=true
// file=guice-6.0.0.jar dp=guice cg=CHA il=true
// file=gson-2.11.0.jar dp=gson cg=CHA il=true

/**
 * Benchmark for our analysis.
 */
object Benchmark {
  /** FilePath for this analysis. Important for the Output. */
  private var filePath: String = ""

  /**
   * Starts benchmark. Runs the amount of times listed in val times.
   * Gives the option to decide if you want to delete old content of txt.
   * @see [[clearFile]]
   * @param args input from run command - e.g. file=commons-io-2.17.0.jar dp=common-io-libraries cg=CHA il=true
   */
  def main(args: Array[String]): Unit = {
    val times = 10 // here
    if(times < 2){
      throw new IllegalArgumentException("times has to be at least 2.")
    }
    var newArgs = ListBuffer[String]()
    args.foreach{ arg =>
      if(!arg.contains("cg")){
        newArgs += arg
        if(arg.contains("file")){
          filePath = s"src//main//scala//Aufgabe3//Output//${arg.split("=")(1).replace(".jar", "")}.txt"
          clearFile() // set clear = false if you don't want to delete old analyses from .txt-file
        }
      }
    }

    repeat(times){
      newArgs += "cg=CHA"
      // CHA
      new Analyse().run(newArgs.toArray)
      // RTA
      newArgs = newArgs.map(_.replace("CHA", "RTA"))
      new Analyse().run(newArgs.toArray)
      // XTA
      newArgs = newArgs.map(_.replace("RTA", "XTA"))
      new Analyse().run(newArgs.toArray)
    }

    // calculate runtime
    readFile()
  }

  /**
   * Prints the result to a file. Set clear = false to save old content of file.
   * @param clear If true erases old content of file.
   */
  private def clearFile(clear: Boolean = true): Unit = {
    val writer = new PrintWriter(new FileWriter(filePath, !clear)) // init writer
    try {
      writer.print("") // write into file
    } finally {
      writer.close() // close writer
    }
  }

  /**
   * Read content of file.
   */
  private def readFile(): Unit = {
    val reader = new BufferedReader(new FileReader(filePath))
    var input = ""
    try {
      var line = reader.readLine() // Read the first line
      while (line != null) {
        input += s"$line\n" // Print the current line
        line = reader.readLine() // Read the next line
      }
    } finally {
      reader.close() // Always close the reader to release resources
    }
    extractLaufzeiten(input)
  }

  /**
   * Extracts runtimes
   * @param input String from which we want to extract the runtime.
   */
  private def extractLaufzeiten(input: String): Unit = {
    // Definiere das Regex-Muster für "Laufzeit: <Zahl> s"
    val laufzeitPattern: Regex = """Laufzeit:\s([\d\.]+)\s*s""".r

    // Finde alle Übereinstimmungen im Eingabestring und extrahiere die Laufzeiten
    val laufzeiten = laufzeitPattern.findAllMatchIn(input).map(_.group(1)).toList

    val mittelWert: ListBuffer[Double] = ListBuffer.fill(6)(0.0)
    for(i <- laufzeiten.indices){
      if(i>=6){ // the first run is always wierd
        mittelWert(i%6) += laufzeiten(i).toDouble
      }
    }
    val times = (laufzeiten.size/6)-1 // the first run gets ignored because of optimization
    println("Laufzeiten: ")
    println(s"CHA\nPublic:${mittelWert.head/times}\nAPI:${mittelWert(1)/times}")
    println(s"RTA\nPublic:${mittelWert(2)/times}\nAPI:${mittelWert(3)/times}")
    println(s"XTA\nPublic:${mittelWert(4)/times}\nAPI:${mittelWert(5)/times}")

  }
}
