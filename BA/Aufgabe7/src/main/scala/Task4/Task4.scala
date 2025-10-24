package Task4

import Util.ComparableObject

import java.io.File
import scala.collection.mutable.ListBuffer

object Task4 {

  /** Indicates whether the project is a library. */
  private var projectIsLibrary: Boolean = _
  /** Stores the chosen call graph algorithm. */
  private var callGraphAlgorithm: String = _
  /** Stores the markers for all methods being searched for, as [[ComparableObject]]. Used as dummies. */
  private val searchFor: ListBuffer[ComparableObject] = ListBuffer[ComparableObject]()
  /** File Path for output and input for this task */
  private var filePath: String = _

  private var targetPath: String = _

  def init(key: String, isLibrary: Boolean, searchFor: List[String], filePath: String, targetPath: String): Unit = {
    this.projectIsLibrary = isLibrary
    this.callGraphAlgorithm = key
    searchFor.foreach{ ele =>
      this.searchFor += new ComparableObject(ele)
    }
    this.filePath = filePath
    this.targetPath = targetPath
  }

  def analyse4_1(): Unit = {
    val projectFile = getFile(new java.io.File(filePath))
    task4_1.main(projectFile, projectIsLibrary, callGraphAlgorithm, searchFor, targetPath)
  }

  def analyse4_2(): Unit = {
    val projectFile = getFile(new java.io.File(filePath))
    val libraryFile = getLibrary(new java.io.File(filePath))
    if(libraryFile != null){
      task4_2.main(projectFile, projectIsLibrary, callGraphAlgorithm, libraryFile, targetPath)
    }
    else{
      throw new IllegalStateException("Task4_2 needs Lib-Folder...")
    }
  }

  private def getFile(folder: File): File = {
    val filesList = folder.listFiles()
    val projectList = filesList.filter(file => file.getPath.split('.').last == "jar")
    // only one jar as project
    if(projectList.length != 1){
      throw new IllegalStateException("Task1")
    }
    projectList.head
  }

  private def getLibrary(folder: File): Array[File] = {
    val filesList = folder.listFiles()
    val projectList = filesList.filter(file => file.getName == "Lib")
    // only one jar as project
    if(projectList.length != 1){
      return null
    }
    projectList.head.listFiles().filter(file => file.getPath.split('.').last == "jar")
  }
}
