package Main
import JSONObject.JSON
import Task1.Task1
import Task2.Task2
import Task3.Task3
import Task4.Task4
import Task5.Task5
import Task6.Task6

import java.io.{BufferedWriter, File, FileWriter, PrintWriter}
import scala.io.Source
// filepath to configs:
object Main {

  private var output: String = _
  private var targetPath: File = _

  def main(args: Array[String]): Unit = {
    if(args.isEmpty){
      JSON.build("")
    }
    else{
      JSON.build(args.head+"/")
    }
    targetPath = JSON.getTargetPath()
    val list = JSON.getConfig
    if(list.head){
      try{
        Task1.analyse()
      } catch{
        case e: Exception => printResultToFile("Task1.txt", e.toString)
      }
      allTogether("Task1.txt")
    }
    if(list(1)){
      try{
        Task2.analyse()
      } catch{
        case e: Exception => printResultToFile("Task2.txt", e.toString)
      }
      allTogether("Task2.txt")
    }
    if(list(2)){
      try{
        Task3.analyse()
      } catch{
        case e: Exception => printResultToFile("Task3.txt", e.toString)
      }
      allTogether("Task3.txt")
    }
    if(list(3)){
      try {
        Task4.analyse4_1()
      } catch {
        case e: Exception => {
          printResultToFile("Task4_1.txt", e.toString)
        }
      }
      try {
        Task4.analyse4_2()
      } catch {
        case e: Exception =>
          printResultToFile("Task4_2.txt", e.toString)
      }
      allTogether("Task4_1.txt")
      allTogether("Task4_2.txt")
    }
    if(list(4)){
      try{
        Task5.analyse()
      } catch{
        case e: Exception => printResultToFile("Task5.txt", e.toString); printResultToFile("Task5.json", e.toString)
      }
      allTogether("Task5.txt")
      allTogether("Task5.json")
    }
    if(list(5)){
      try{
        Task6.analyse()
      } catch{
        case e: Exception => printResultToFile("Task6.txt", e.toString)
      }
      allTogether("Task6.txt")
    }
    print()
  }

  /**
   * Adds log of this task to output
   */
  private def allTogether(task: String): Unit = {
    try {
      println(s"Reading file: $task")
      val source = Source.fromFile(targetPath+ "/" + task)
      val content = source.getLines().toList
      source.close()
      output += content.mkString("\n")
      output += "\n-------------------------------------------------------\n"
    } catch {
      case e: Exception =>
        println(s"Error reading file ${targetPath+ "/" + task}: ${e.getMessage}")
    }
  }

  /**
   * Prints output into file
   */
  private def print(): Unit = {
    // Create a PrintWriter to write to the file
    val writer = new PrintWriter(targetPath + "/" + "Result.txt")
    // Write content to the file
    writer.println(output)
    // Close the writer to release resources
    writer.close()
  }

  /**
   * Prits the result of this analysis into a file with [[targetPath]] as its path.
   */
  private def printResultToFile(task: String, output: String): Unit = {
    // Create a PrintWriter to write to the file
    val writer = new PrintWriter(targetPath + "/" + task)
    // Write content to the file
    writer.println(output)
    // Close the writer to release resources
    writer.close()
  }
}
