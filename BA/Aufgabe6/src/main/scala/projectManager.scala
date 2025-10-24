import com.typesafe.config.ConfigValueFactory
import org.opalj.br.{BaseConfig, ClassFile, Field, FieldType}
import org.opalj.br.analyses.Project
import org.opalj.br.reader.{Java11Framework, Java17Framework}
import org.opalj.log.{Error, GlobalLogContext, LogContext, LogMessage, OPALLogger}
import org.opalj.tac.cg.{CallGraph, XTACallGraphKey}

import java.io.File
import java.net.URL
import scala.annotation.unused
import scala.collection.mutable

class projectManager {

  /** Maps jar to package. */
  var jarToPackage: mutable.Map[String, Set[String]] = _
  /** Maps package to classes. */
  var packageToClasses: mutable.Map[String, Set[ClassFile]] = _
  /** Maps package to jar */
  var packageToJar: mutable.Map[String, List[String]] = _
  /** Maps class to package */
  var classToPackage: mutable.Map[String, List[String]] = _
  /** Holds a Map for all [[Rule]]s. Made by [[CheckJSON]]. */
  private var ruleMapper: mutable.LinkedHashMap[(String, String), (String, Int)] = _
  /** Created Project */
  private var project: Project[URL] = _
  /** Created CallGraph - only uses [[XTACallGraphKey]]  */
  private var callGraph: CallGraph = _

  /** Constructor */
  def this(projectFiles: Array[File], libFiles: Array[File], isLibrary: Boolean) = {
    this()
    project = loadProject(projectFiles, libFiles, isLibrary)
    //callGraph = project.get( XTACallGraphKey )
    val cf = project.allProjectClassFiles.map{ele => ele.fqn.split("/").dropRight(1).mkString("/")}
    jarToPackage = mutable.Map(projectFiles.head.getName -> project.classesPerPackage.keys.toSet.filter{pack =>
      cf.contains(pack)
    })
    packageToClasses = mutable.Map(project.classesPerPackage.filter{ele => cf.contains(ele._1)}.toSeq:_*)
    loadLibraries(libFiles)
    reverseMapAll()
    // ----- only for testing ----- //
    //printMapOfMaps()
    //printReverseMapOfMaps()
    //classToPackage.keys.foreach(println(_))
    // ----- //
  }

  /** Reverse maps [[jarToPackage]] and [[packageToClasses]] into [[packageToJar]] and [[classToPackage]]. */
  private def reverseMapAll(): Unit = {
    packageToJar = mutable.Map()
    jarToPackage.foreach{ ele =>
      ele._2.toList.foreach{ pack =>
        packageToJar.update(pack, packageToJar.getOrElse(pack.replace("/", "."), Nil) ++ List(ele._1))
      }
    }
    classToPackage = mutable.Map()
    packageToClasses.foreach{ ele =>
      ele._2.toList.foreach{ pack =>
        classToPackage.update(pack.fqn, classToPackage.getOrElse(pack.fqn, Nil) ++ List(ele._1.replace("/", ".")))
      }
    }
  }

  /** Loads files for each library. And adds them to [[jarToPackage]] and [[packageToClasses]]. */
  private def loadLibraries(libFiles: Array[File]): Unit = {
    var config = BaseConfig
    // set entry point of library
    config = config.withValue(
      "org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis",
      ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryEntryPointsFinder")
    ).withValue(
      "org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis",
      ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder")
    )
    libFiles.foreach{ file =>
      val project = Project(file, GlobalLogContext, config)
      val cf = project.allProjectClassFiles.map{ele => ele.fqn.split("/").dropRight(1).mkString("/")}
      mutable.Map(file.getName -> project.classesPerPackage.keys.toSet.filter{pack =>
        cf.contains(pack)
      }).foreach{ ele =>
        jarToPackage += ele
      }
      mutable.Map(project.classesPerPackage.filter{ele => cf.contains(ele._1)}.toSeq:_*).foreach(packageToClasses += _)
    }
  }

  /**
   * Used to check if given combination is a valid call - valid calls are correctly leveled calls.
   * @param last The [[Rule.to]] or [[Rule.from]] of the higher leveled [[Rule]].
   * @param now The [[Rule.to]] or [[Rule.from]] of the lower leveled [[Rule]].
   */
  def validate(last: String, now: String): Boolean = {
    val packageList = this.jarToPackage.getOrElse(last.replace("/", "."), Nil)
    val classList = this.packageToClasses.getOrElse(last.replace(".", "/"), Nil)
    // last was jar - has to be either package or class now
    if(packageList != Nil){
      // now is in the found packages
      if(packageList.exists(_ == now.replace(".", "/"))) true
      // could be a correlated class
      else{
        val classList = packageList.flatMap{ pack =>
          val list = packageToClasses.getOrElse(pack, null)
          if(list==null){
            throw new IllegalArgumentException("Ja keine Ahnung")
          }
          list.map{ c =>
            c.fqn
          }
        }
        classList.exists(_ == now.replace(".", "/"))
      }
    }
    // last was package - has to be class now
    else if(classList != Nil){
      classList.exists(cf => cf.fqn == now.replace(".", "/"))
    }
    // other options always 'false'
    else{
      false
    }
  }

  /** Creates project. */
  private def loadProject(projectFiles: Array[File], libFiles: Array[File], isLibrary: Boolean): Project[URL] = {
    // Initial configuration
    var config = BaseConfig

    // Adjustment of the configuration according to the type of the software system.
    if (isLibrary) {
      // set entry point of library
      config = config.withValue(
        "org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis",
        ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryEntryPointsFinder")
      ).withValue(
        "org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis",
        ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder")
      )
    }
    val logg: OPALLogger = new MyLogger
    OPALLogger.updateLogger(GlobalLogContext, logg)
    val libraryClassFiles = Java17Framework.AllClassFiles ( libFiles )
    val projectClassFiles = Java17Framework.AllClassFiles ( projectFiles )

    Project ( projectClassFiles , libraryClassFiles ,
      libraryClassFilesAreInterfacesOnly = false ,
      virtualClassFiles = Iterable.empty)( config, logg )
  }

  /** Stops print into console when project is created. */
  private class MyLogger extends OPALLogger {
    override def log(message: LogMessage)(implicit ctx: LogContext): Unit = {
      message.level match {
        /*
        case Warn =>
          println(s"[WARN] ${message.message}")
         */
        case Error =>
          println(s"[ERR] ${message.message}")
        case _ =>
      }
    }
  }

  /** Returns both maps [[jarToPackage]] and [[packageToClasses]]. */
  def getMaps: (Map[String, Set[String]], Map[String, Set[ClassFile]]) = {
    (this.jarToPackage.toMap, this.packageToClasses.toMap)
  }

  /** Prints [[jarToPackage]] and [[packageToClasses]]*/
  @unused
  def printMapOfMaps(): Unit = {
    jarToPackage.foreach{ jar =>
      println(jar._1)
      jar._2.foreach{ `package` =>
        println(s"    ${`package`}")
        packageToClasses(`package`).foreach{ cf =>
          println(s"        ${cf.fqn}")
        }
      }
    }
  }

  /** Prints [[packageToJar]] and [[classToPackage]]*/
  @unused
  private def printReverseMapOfMaps(): Unit = {
    packageToJar.foreach(println(_))
    //classToPackage.foreach(println(_))
  }

  /** Checks if jar exists.*/
  def jarExists(jarName: String): Boolean = {
    this.jarToPackage.getOrElse(jarName, null)!=null
  }

  /** Checks if package or class exists */
  def packageOrClassExists(packageOrClassName: String): Boolean = {
    this.packageToClasses.getOrElse(packageOrClassName.replace(".", "/"), Nil)!=Nil || this.classToPackage.getOrElse(packageOrClassName.replace(".", "/"), Nil)!=Nil
  }

  /** Sets the [[ruleMapper]]. */
  def setRuleMapper(ruleMapper: mutable.LinkedHashMap[(String, String), (String, Int)]): Unit = {
    this.ruleMapper = ruleMapper
  }

  /** Returns a triple of ([[project]], [[callGraph]], [[ruleMapper]]) */
  def getProjectAndCallGraphAndRuleMapper: (Project[URL], CallGraph, mutable.LinkedHashMap[(String, String), (String, Int)]) = {
    (project, callGraph, ruleMapper)
  }

  /** Returns the value for the given key for [[jarToPackage]]. */
  def getJarToPackage(key: String): List[String] = {
    packageToJar.getOrElse(key.replace(".", "/"), null)
  }

  /** Checks if class exists and returns package. */
  def getPackageOfClass(clas: String): List[String] = {
    classToPackage.getOrElse(clas.replace(".", "/"), null)
  }

  /** Checks if class with this name exists. */
  def isClass(field: Field): Boolean = {
    // for arrays
    var output = field.fieldType.toJava
    //println(s"className: ${field.classFile.fqn}, fieldName: ${field.name}\n field: $field")
    if(field.attributes.nonEmpty){
      //println(s"${field.classFile.fqn} ${field.name}")
      output = cutClassType(field.attributes.toString())
      //println(output)
    }
    val newCN = output.replace(".", "/").replace("[]", "")
    //println(newCN)
    classToPackage.getOrElse(newCN, null) != null
  }

  /**
   * Cut Variable classType from array
   */
  def cutClassType(attribute : String): String = {
    // ----- variable type ----- //
    // first cut
    var index = attribute.indexOf("signature=ClassTypeSignature")
    var result = attribute.substring(index)
    //println(result)
    // second cut
    index = result.indexOf("SimpleClassTypeSignature")
    var clas = result
    result = result.substring(index)
    //println(result)
    // last cut
    result = result.split('(')(1).split(",").head
    //println(result)
    val typ = result
    //println()
    // ----- variable class ----- //
    clas = clas.split(')').head
    index = clas.indexOf("Some")
    clas = clas.substring(index).replace("Some(", "").replace(")", "")
    //println(clas)
    s"$clas$typ"
  }
}
