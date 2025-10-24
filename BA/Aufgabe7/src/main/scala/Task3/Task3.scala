package Task3

import java.io.File

// file=commons-collections4-4.5.0-M2.jar dp=commons-collection cg=CHA il=true
// file=pdfbox-app-3.0.3.jar dp=pdfbox cg=CHA il=false
// file=gson-2.11.0.jar dp=gson cg=CHA il=true

/**
 * Normal run for our application.
 */
object Task3 {

  private var filePath: File = _
  private var libraryPath: File = _
  private var isLibrary: Boolean = _
  private var key: String = _
  private var targetPath: String = _

  def init(key: String, isLibrary: Boolean, filePath: String, targetPath: String): Unit = {
    this.isLibrary = isLibrary
    this.key = key
    this.filePath = getFile(new java.io.File(filePath))
    this.libraryPath = getLibrary(new java.io.File(filePath))
    this.targetPath = targetPath
  }

  /**
   * Starts an analysis.
   * Gives the option to decide if you want to delete old content of txt.
   */
  def analyse(): Unit = {
    if(libraryPath != null){
      new Analyse().run(filePath: File, libraryPath: File, isLibrary: Boolean, key: String, targetPath)
    }
    else{
      throw new IllegalStateException("Task3 needs a Lib folder...")
    }
  }

  private def getFile(folder: File): File = {
    val filesList = folder.listFiles()
    val projectList = filesList.filter(file => file.getPath.split('.').last == "jar")
    // only one jar as project
    if(projectList.length != 1){
      throw new IllegalStateException("Task3")
    }
    projectList.head
  }

  private def getLibrary(folder: File): File = {
    val filesList = folder.listFiles()
    val projectList = filesList.filter(file => file.getName == "Lib")
    // only one jar as project
    if(projectList.length != 1){
      return null
    }
    projectList.head
  }
}


