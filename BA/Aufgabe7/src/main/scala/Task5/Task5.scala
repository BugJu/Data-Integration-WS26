package Task5

import java.io.File

object Task5 {

  private var domainIndex: Int = _
  private var filePath: String = _
  private var targetPath: String = _

  def init(domainIndex: Int, filePath: String, targetPath: String): Unit = {
    this.filePath = filePath
    this.domainIndex = domainIndex
    this.targetPath = targetPath
  }

  def analyse(): Unit = {
    val project = getFile(new java.io.File(filePath))
    new task5_1(project, domainIndex, targetPath)
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
}
