package Aufgabe3

import java.io.{FileWriter, PrintWriter}

// file=commons-collections4-4.5.0-M2.jar dp=commons-collection cg=CHA il=true
// file=pdfbox-app-3.0.3.jar dp=pdfbox cg=CHA il=false
// file=gson-2.11.0.jar dp=gson cg=CHA il=true

/**
 * Normal run for our application.
 */
object task3 {

  /**
   * Starts an analysis.
   * Gives the option to decide if you want to delete old content of txt.
   * @see [[clearFile]]
   * @param args input from run command - e.g. file=commons-io-2.17.0.jar dp=common-io-libraries cg=CHA il=true
   */
  def main(args: Array[String]): Unit = {
    args.foreach { arg =>
      if(arg.contains("file")){
        clearFile(arg.split("=")(1)) // set clear = false if you don't want to delete old analyses from .txt-file
      }
    }
    new Analyse().run(args)
  }

  /**
   * Prints the result to a file. Set clear = false to save old content of file.
   * @param file The file to which the result is printed.
   * @param clear If true erases old content of file.
   */
  private def clearFile(file: String, clear: Boolean = true): Unit = {
    val filePath = s"src//main//scala//Aufgabe3//Output//${file.replace(".jar", "")}.txt"
    val writer = new PrintWriter(new FileWriter(filePath, !clear)) // init writer
    try {
      writer.print("") // write into file
    } finally {
      writer.close() // close writer
    }
  }
}


