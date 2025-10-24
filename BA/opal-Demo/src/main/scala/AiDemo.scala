import java.io.File
import org.opalj.br.analyses.Project

import org.opalj.ai.AIResult
import org.opalj.ai.BaseAI
import org.opalj.ai.common.DomainRegistry
import org.opalj.ai.common.XHTML

import java.nio.file.{Files, Path}
import scala.io.StdIn


/**
 * Demonstrates how to execute an abstract interpretation run for all methods of a program.
 *
 * Based on: https://github.com/opalj/opal/blob/develop/DEVELOPING_OPAL/demos/src/main/java/org/opalj/br/analyses/ProjectDemo.java
 *
 * @author Michael Eichberg, Johannes Düsing
 */
object AiDemo extends App {

  /**
   * Simple method to select which domain shall be used for abstract interpretation. In interactive mode, the user can
   * select one of the available domains from the DomainRegistry. In non-interactive mode, the first domain is automatically
   * selected.
   *
   * Domains dictate how precise the tracking of variable values is during abstract interpretation.
   *
   * @param interactive True if interactive selection shall be used
   * @return A string identifier of the selected domain
   */
  def selectDomain(interactive: Boolean): String = {
    val domainDescriptions = DomainRegistry.domainDescriptions

    var cnt = 0
    val domainMap = domainDescriptions.map{ descr =>
      val t = (cnt, descr)
      cnt += 1
      t
    }.toMap

    println("The available domains are: ")
    domainMap.foreach{ tuple =>
      println(s"\t [${tuple._1}] - ${tuple._2}")
    }

    if(interactive){
      println("Enter domain number to proceed")
      val input = StdIn.readInt()
      if(input < 0 || input >= cnt - 1){
        println("Invalid domain number selected, exiting.")
        System.exit(1)
      }
      domainMap(input)
    } else {
      val theDomain = domainDescriptions.head
      println(s"Automatically selected domain $theDomain")
      theDomain
    }
  }

  /// --- START OF ACTUAL APPLICATION CODE


  if(args.length < 1 || args(0).isBlank) {
    println("USAGE: AiDemo <path-to-jar-or-class> [--interactive] [--write-results]")
    System.exit(-1)
  }

  val useInteractive = args.exists(_.equalsIgnoreCase("--interactive"))
  val writeResults = args.exists(_.equalsIgnoreCase("--write-results"))

  // Create a simple Project instance for the file given via CLI arguments
  val project = Project(new File(args(0)))

  // Create an abstract interpreter (the same instance can be reused)
  val ai = new BaseAI(true, false)


  // Select a domain for the abstract interpretation
  var domainIdentifierStr = selectDomain(useInteractive)

  // This is needed because there is likely a bug in the way OPAL 5.0.0 handles domain identifiers.
  domainIdentifierStr = domainIdentifierStr.substring(domainIdentifierStr.indexOf("]") + 2)

  var methodCnt = 0

  // Iterate over all class files in the project
  project.allClassFiles.foreach{ classFile =>

    // Iterate over all methods in the project
    classFile.methods.foreach{ method =>
      // We can only start abstract interpretation when there is code inside the method
      if(method.body.isDefined){
        // Build the domain object
        val domain = DomainRegistry.newDomain(domainIdentifierStr, project, method)

        // Run the actual abstract interpretation for the current method using the interpreter and domain defined above
        val result: AIResult = ai(method, domain)

        val methodCode = result.code
        val evaluatedInstructionPCs = result.evaluatedPCs

        // If the abstract interpretation evaluated (looked at) less instructions than there are in the code, that means
        // there are instructions that are not reachable at all!
        if(evaluatedInstructionPCs.size < methodCode.size){
          println(s"${methodCode.size - evaluatedInstructionPCs.size} instructions not reachable in ${method.fullyQualifiedSignature}")
        }

        // If specified via CLI arguments, an HTML report will be written for every method
        if(writeResults){
          val rep = XHTML.dump(classFile, method, "Abstract Interpretation Succeeded", result).toString()

          Files.writeString(Path.of("ai-data", s"$methodCnt-${method.name.replace("<", "").replace(">", "")}.html"), rep)
          methodCnt += 1
        }
      }
    }
  }

}
