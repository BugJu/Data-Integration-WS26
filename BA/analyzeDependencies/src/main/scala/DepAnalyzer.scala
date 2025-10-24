
object DepAnalyzer {
  def main(args: Array[String]): Unit = {
    val analyzer = new Analyzer()
    val anmal = analyzer.loadProject(analyzer.projectFiles, analyzer.libraryFiles)
    println(analyzer.getLibraryMethods)
    println(analyzer.getCallGraph(anmal).numEdges)
    analyzer.countMethods(anmal, analyzer.getLibraryMethods)

  }


}
