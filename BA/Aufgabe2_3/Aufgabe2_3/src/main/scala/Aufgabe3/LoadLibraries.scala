package Aufgabe3

import java.io.File
import scala.collection.mutable.ListBuffer

/**
 * Load all paths to the library files
 */
class LoadLibraries {

  /**
   * Builds all paths from libraries in the folder given as dp=
   * @return list of all paths
   */
  def getClassesAndMethodsOfLibrary(path: String): Array[File] = {
    val pathToDirectory = s"src//main//scala//Aufgabe3//Projekte//$path//"
    val directory = new File(pathToDirectory)

    val arrayOfFiles = ListBuffer[File]()

    // check if directory exist
    if (directory.exists && directory.isDirectory) {
      val filesAndDirs = directory.listFiles()
      filesAndDirs.foreach { file =>
        arrayOfFiles += new java.io.File(pathToDirectory + file.getName)
      }
    } else {
      throw new IllegalArgumentException(s"${directory.getPath} ist kein gültiges Verzeichnis")
    }
    arrayOfFiles.toArray
  }
}
