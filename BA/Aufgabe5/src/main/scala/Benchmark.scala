import org.opalj.ai.common.DomainRegistry

/** Used by python script to make an analysis. */
object Benchmark {

  /** Contains the path to the chosen file */
  private var projectFile: String = ""
  /** Contains the domain name */
  private var domain: String = ""
  /** Contains benchmark folder name */
  private var benchmarkName: String = ""

  /** Called by the python script */
  def main(args : Array[String]): Unit = {
    //outputDomains()
    if(true){
      readInput(args)
      new task5(projectFile, domain, benchmarkName)
    }
  }

  /** Sets the global variables
   * @param args Input from cli
   * @throws IllegalArgumentException If one of the variables isn't set*/
  def readInput(args: Array[String]): Unit = {
    args.foreach { arg =>
      if (arg.contains("file")) {
        projectFile = arg.split("=").last
      }
      else if (arg.contains("domain")) {
        domain = getWholeDomain(arg.split("=").last)
      }
      else if(arg.contains("benchmark")){
        benchmarkName = arg.split("=").last
      }
    }
    if(projectFile == ""){
      throw new IllegalArgumentException(s"No project was set. {${args.toList}}")
    }
    if(domain == ""){
      throw new IllegalArgumentException(s"No domain was set. {${args.toList}}")
    }
    if(benchmarkName == ""){
      throw new IllegalArgumentException(s"No benchmark folder was set. {${args.toList}}")
    }
  }

  def outputDomains(): Unit = {
    DomainRegistry.domainDescriptions.map{ descr =>
      descr
    }.foreach(println(_))
  }

  /**
   * Adds domain description to domain
   * @param input Chosen domain name
   * @return whole domain description
   */
  def getWholeDomain(input: String): String = {
    DomainRegistry.domainDescriptions.filter( domain => domain.split(" ").head == input).head
  }

  /**
   * Used for tests
   */
  def getVariables: Array[String] = {
    Array(projectFile, domain, benchmarkName)
  }

  /**
   * Resets the state - for testing
   */
  def resetState(): Unit = {
    projectFile = ""
    domain = ""
    benchmarkName = ""
  }
}
