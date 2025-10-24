package Task6

import JSONObject.JSON.Specification

import java.io.File

object Task6 {

  /** JSON-File as a usable object of [[Specification]] */
  var jsonInput: Specification = _
  /** Boolean value to check that project is a library or not (important for starting point) */
  var isLibrary: Boolean = _
  /** FilePath */
  var filePath: String = _

  var targetPath: String = _

  def init(jsonInput: Specification, isLibrary: Boolean, filePath: String, targetPath: String): Unit = {
    this.filePath = filePath
    this.jsonInput = jsonInput
    this.isLibrary = isLibrary
    this.targetPath = targetPath
  }

  def analyse(): Unit = {
    val project = getFile(new java.io.File(filePath))
    val libraries = getLibrary(new java.io.File(filePath))
    if(libraries != null){
      task6_1.main(jsonInput, isLibrary, Array(project), libraries, targetPath)
    }
    else{
      throw new IllegalStateException("Task6 needs a lib folder in the filePath directory.")
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
