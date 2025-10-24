package Task5

import org.opalj.br.BaseConfig
import org.opalj.ai.{AIResult, BaseAI, InterpretationFailedException}
import org.opalj.ai.common.DomainRegistry
import org.opalj.br.analyses.Project
import org.opalj.log.{Error, GlobalLogContext, LogContext, LogMessage, OPALLogger}

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}
import org.joda.time.DateTime
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json.Json

import java.nio.charset.StandardCharsets
import scala.collection.mutable.ListBuffer

class task5_1 {

  /** Stores the path to the project. */
  private var projectFile: File = _
  private var chosenDomain: Int = _

  private var targetPath: String = _

  /**
   * Constructor
   * @param projectFile File - Example.jar (files have to be in 'Projects' folder)
   * @param domainIndex Domain index - Name and description
   */
  def this(projectFile : File, domainIndex : Int, targetPath: String) = {
    this()
    this.projectFile = projectFile
    if(!projectFile.exists()){
      throw new IllegalArgumentException("Can't find ProjectFile")
    }
    chosenDomain = domainIndex
    this.targetPath = targetPath
    main()
  }

  /** Main-Method */
  private def main(): Unit = {
    // ---------- project ---------- //
    // Create a simple Project instance for the file given via CLI arguments
    OPALLogger.updateLogger(GlobalLogContext, new MyLogger)
    /** Holds the project */
    val project = Project(projectFile, GlobalLogContext, BaseConfig)

    // ---------- domain ---------- //
    /** Create an abstract interpreter (the same instance can be reused) */
    val ai = new BaseAI(true, false)

    /** Holds the chosen domain */
    val domain: String = selectDomain()
    // This is needed because there is likely a bug in the way OPAL 5.0.0 handles domain identifiers.
    val domainIdentifierStr = domain.substring(domain.indexOf("]") + 2)

    // ---------- basic inits. ---------- //
    /** Starts timer */
    val timeBefore = System.currentTimeMillis()
    /** Gets every method */
    val methodsBuffer = ListBuffer[MethodWithDeadCode]()

    var output = ""

    // ---------- iterate ---------- //
    // Iterate over all class files in the project
    val result = project.allClassFiles.foldLeft((0, 0)) { (acc, classFile) =>
      // acc._1 ist die Gesamtzahl der Klassen, acc._2 ist die Gesamtzahl der Methoden
      (acc._1 + 1, acc._2 + classFile.methods.size)
    }
    var counter = 0
    project.allClassFiles.foreach{ classFile =>
      if(counter%3000 == 0){
        println(s"$counter / ${result._1}" )
      }
      counter += 1
      // Iterate over all methods in the project
      classFile.methods.foreach{ method =>
        // it exists a problem which throws 'InterpretationFailedException'
        try{
          // We can only start abstract interpretation when there is code inside the method
          if(method.body.isDefined){
            // ---------- Domain ---------- //
            // Build the domain object
            val buildDomain = DomainRegistry.newDomain(domainIdentifierStr, project, method)
            // Run the actual abstract interpretation for the current method using the interpreter and domain defined above
            val result: AIResult = ai(method, buildDomain)

            // ---------- basic information for output json ---------- //
            // holds instructions description
            val methodCode = result.code
            // holds pc
            val evaluatedInstructionPCs = result.evaluatedPCs
            val foundPC: Array[Int] = evaluatedInstructionPCs.map(ele => ele).toArray

            // ---------- check if method contains dead code ---------- //
            // If the abstract interpretation evaluated (looked at) fewer instructions than there are in the code, that means
            // there are instructions that are not reachable at all!
            if(evaluatedInstructionPCs.size < methodCode.size){
              output += s"${methodCode.size - evaluatedInstructionPCs.size} instructions not reachable in ${method.fullyQualifiedSignature}\n"

              // get dead instructions
              val deadInstructions: List[DeadInstruction]  = methodCode.filter(instr => !foundPC.contains(instr.pc))
                .map(instr => DeadInstruction(stringRepresentation = instr.instruction.toString, programCounter = instr.pc) ).toList
              // add method
              methodsBuffer += MethodWithDeadCode.apply(fullSignature = method.fullyQualifiedSignature, numberOfTotalInstructions = methodCode.size, numberOfDeadInstructions = methodCode.size - evaluatedInstructionPCs.size, enclosingTypeName = method.classFile.thisType.toJava, deadInstructions = deadInstructions)
            }
          }
          printResult(output)
        }catch{
          case e: InterpretationFailedException => println(e)
        }
      }
    }
    // ---------- report ----------
    val report = DeadCodeReport.apply(filesAnalyzed = List[String](projectFile.toString.split('\\').last), domainUsed = domain, timeFinished = DateTime.now(), totalRuntimeMs = System.currentTimeMillis()-timeBefore, methodsFound = methodsBuffer.toList)

    // convert report to json
    val json = Json.toJson(report)
    // get correct path
    val domainName = s"${domain.split('.')(domain.split('.').length-2)}${domain.split('.').last.split("]").head}"
    // add new file (not overwriting)
    Files.write(Paths.get(s"$targetPath/Task5.json") , Json.prettyPrint(json).getBytes(StandardCharsets.UTF_8))
  }

  /**
   * Select the used domain
   */
  private def selectDomain(): String = {
    val domainDescriptions = DomainRegistry.domainDescriptions

    var cnt = 0
    val domainMap = domainDescriptions.map{ descr =>
      val t = (cnt, descr)
      cnt += 1
      t
    }.toMap

    if(true){
      if(chosenDomain < 0 || chosenDomain >= cnt - 1){
        println("Invalid domain number selected, exiting.")
        System.exit(1)
      }
      domainMap(chosenDomain)
    } else {
      val theDomain = domainDescriptions.head
      println(s"Automatically selected domain $theDomain")
      theDomain
    }
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

  /**
   * Prints the result of this analysis into a file with [[targetPath]] as its path.
   */
  private def printResult(output: String): Unit = {
    // Specify the file name
    val fileName = "Task5.txt"
    // Create a PrintWriter to write to the file
    val writer = new PrintWriter(targetPath + "/" + fileName)
    // Write content to the file
    writer.println(output)
    // Close the writer to release resources
    writer.close()
  }
}
