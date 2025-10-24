import org.opalj.br.BaseConfig
import org.opalj.ai.{AIResult, BaseAI, InterpretationFailedException}
import org.opalj.ai.common.DomainRegistry
import org.opalj.br.analyses.Project
import org.opalj.log.{Error, GlobalLogContext, LogContext, LogMessage, OPALLogger}

import java.io.File
import java.nio.file.{Files, Paths}
import org.joda.time.DateTime
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json.Json

import java.nio.charset.StandardCharsets
import scala.collection.mutable.ListBuffer

// sbt clean coverage test coverageReport
// Benchmark python BenchmarkTest.py
class task5 {

  /** Stores the path to the project. */
  private var projectFile: File = _
  /** Indicates whether the project is a library. */
  private var chosenDomain: String = _
  /** File Path for output and input for this task */
  private val path: String = "src/main/scala/"
  /** Benchmark */
  private var benchmark: String = _

  /**
   * Constructor
   * @param projectName File name - Example.jar (files have to be in 'Projects' folder)
   * @param domainName Domain name - Name and description
   * @param benchmark Only used by 'Benchmark.scala' - Changes the output into a benchmark folder
   */
  def this(projectName : String, domainName : String, benchmark: String = "") = {
    this()
    projectFile = new java.io.File(s"${path}Projects/$projectName/")
    if(!projectFile.exists()){
      throw new IllegalArgumentException("Can't find ProjectFile")
    }
    chosenDomain = domainName
    this.benchmark = benchmark
    if(benchmark!=""){
      this.benchmark += "/"
      Files.createDirectories(Paths.get(s"${path}Results/$benchmark"))
      println(Paths.get(s"${path}Results/$benchmark"))
    }
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
    /** Only filled if Benchmark-Test */
    val filler = benchmark

    // ---------- iterate ---------- //
    // Iterate over all class files in the project
    project.allClassFiles.foreach{ classFile =>
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
              println(s"${methodCode.size - evaluatedInstructionPCs.size} instructions not reachable in ${method.fullyQualifiedSignature}")

              // get dead instructions
              val deadInstructions: List[DeadInstruction]  = methodCode.filter(instr => !foundPC.contains(instr.pc))
                .map(instr => DeadInstruction(stringRepresentation = instr.instruction.toString, programCounter = instr.pc) ).toList
              // add method
              methodsBuffer += MethodWithDeadCode.apply(fullSignature = method.fullyQualifiedSignature, numberOfTotalInstructions = methodCode.size, numberOfDeadInstructions = methodCode.size - evaluatedInstructionPCs.size, enclosingTypeName = method.classFile.thisType.toJava, deadInstructions = deadInstructions)
            }
          }
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
    val dirPath = s"${path}Results/$filler/${projectFile.getName.replace(".", "_").replace("_jar", "")}"
    val id = getReportID(s"${dirPath}_${domainName}_")
    Files.write(Paths.get(s"${dirPath}_${domainName}_$id.json") , Json.prettyPrint(json).getBytes(StandardCharsets.UTF_8))
  }

  /**
   * Checks if report for the project and domain already exists and gives the report another name.
   * @param path Path to this report
   * @return ID that is free
   */
  private def getReportID(path: String): Int = {
    var index = 1
    while(new java.io.File(s"$path$index.json").exists()){
      index += 1
    }
    index
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

    val domainIndex = domainMap.filter(ele => ele._2 == chosenDomain).last._1

    if(true){
      if(domainIndex < 0 || domainIndex >= cnt - 1){
        println("Invalid domain number selected, exiting.")
        System.exit(1)
      }
      domainMap(domainIndex)
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
}
