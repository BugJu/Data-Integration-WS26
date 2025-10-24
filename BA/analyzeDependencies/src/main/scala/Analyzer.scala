import com.typesafe.config.ConfigValueFactory
import org.opalj.br.{BaseConfig, ClassFile}
import org.opalj.br.analyses.Project

import java.io.File
import org.opalj.br.reader.Java17Framework
import org.opalj.tac.cg.{CallGraph, RTACallGraphKey}

import java.net.URL
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps


class Analyzer {

  var libraryFiles: Array[File] = _
  var projectFiles: Array[File] = _

  case class LibraryMethods(classFile: ClassFile, methodName: String)

  //simuliert readIpnut

  //File welches analysiert werden soll (wieviel von den importierten Methoden verwendet werden)
  projectFiles = Array(new java.io.File("Files/Libraries/commons-collections4-4.5.0-M2.jar"))

  //FilePath zu den Library Klassen
  val librariesDir = new File("Files/LibraryFiles/")
  val arrayOfFiles = ListBuffer[File]()
  librariesDir.listFiles().foreach(file => arrayOfFiles += new java.io.File(file.getPath))
  libraryFiles = arrayOfFiles.toArray

  def loadProject(projectFiles: Array[File], libraryFiles: Array[File]): Project[URL] = {
    var config = BaseConfig

    config = BaseConfig.withValue(
      "org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis",
      ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryEntryPointsFinder")
    ).withValue(
      "org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis",
      ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder")
    )
    val libraryClassFiles = Java17Framework.AllClassFiles(libraryFiles)
    val projectClassFiles = Java17Framework.AllClassFiles(projectFiles)

    Project(projectClassFiles, libraryClassFiles, libraryClassFilesAreInterfacesOnly = false, virtualClassFiles = Iterable.empty)(config)
  }

  def getLibraryMethods: ListBuffer[ListBuffer[LibraryMethods]] = {
    val list: ListBuffer[ListBuffer[LibraryMethods]] = ListBuffer[ListBuffer[LibraryMethods]]()

    libraryFiles.foreach { file =>
      val project = loadProject(projectFiles, Array(file))
      val methList = ListBuffer[LibraryMethods]()
      project.allClassFiles.foreach { classFile =>
        classFile.methods.foreach { method =>
          if (method.isPublic)
            methList += LibraryMethods(classFile, method.name)
        }
      }
      list += methList
    }
    list
  }

  def getCallGraph(project : Project[URL]): CallGraph = {
    val callGraph = project.get(RTACallGraphKey)
    callGraph
  }

  def countMethods(project : Project[URL], libraryMethods: ListBuffer[ListBuffer[LibraryMethods]]) = {
    val callGraph = getCallGraph(project)
    val reachableMethdos = callGraph.reachableMethods().toArray
    for(i<- reachableMethdos.indices){
      val method = reachableMethdos(i)
      for(i <- libraryMethods.indices){
        libraryMethods(i).foreach{
          libraryMethod =>
            if(method.method.name == libraryMethod.methodName){
              println(method.method.name)
            }
        }
      }
    }
  }

}