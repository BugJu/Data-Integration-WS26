import org.opalj.ai.common.DomainRegistry
import org.scalatest.funsuite.AnyFunSuite

class BenchmarkTest extends AnyFunSuite{

  //outputDomains()

  /** Test for [[Benchmark.readInput]] */
  test("Benchmark.readInput - all variables set"){
    Benchmark.resetState()
    Benchmark.readInput(Array("file=test.jar", "domain=[org.opalj.ai.fpcf.domain.L1DefaultDomainWithCFGAndDefUseAndSignatureRefinement]", "benchmark=Test"))
    assert(Benchmark.getVariables.sameElements(Array(
      "test.jar",
      "[org.opalj.ai.fpcf.domain.L1DefaultDomainWithCFGAndDefUseAndSignatureRefinement] uses intervals for int values; tracks nullness and must alias information for reference types; records the ai-time def-use information; uses refined signature information",
      "Test"
    )))
  }

  test("Benchmark.readInput - file not set"){
    Benchmark.resetState()
    // without 'file'
    assertThrows[IllegalArgumentException](Benchmark.readInput(Array("domain=[org.opalj.ai.fpcf.domain.L1DefaultDomainWithCFGAndDefUseAndSignatureRefinement]", "benchmark=Test")))
  }

  test("Benchmark.readInput - domain not set"){
    Benchmark.resetState()
    // without 'domain'
    assertThrows[IllegalArgumentException](Benchmark.readInput(Array("file=test.jar", "benchmark=Test")))
  }

  test("Benchmark.readInput - benchmark folder not set"){
    Benchmark.resetState()
    // without 'benchmark'
    assertThrows[IllegalArgumentException](Benchmark.readInput(Array("file=test.jar", "domain=[org.opalj.ai.fpcf.domain.L1DefaultDomainWithCFGAndDefUseAndSignatureRefinement]")))
  }

  test("Benchmark.resetState - sets global variable to default values"){
    Benchmark.resetState()
    Benchmark.readInput(Array("file=test.jar", "domain=[org.opalj.ai.fpcf.domain.L1DefaultDomainWithCFGAndDefUseAndSignatureRefinement]", "benchmark=Test"))
    assert(Benchmark.getVariables.count(ele => !ele.equals("")) == 3)
    Benchmark.resetState()
    assert(Benchmark.getVariables.count(ele => !ele.equals("")) == 0)
  }

  test("Benchmark.getWholeDomain - checks if the description gets added to the domain name"){
    val domain = Benchmark.getWholeDomain("[org.opalj.ai.fpcf.domain.L1DefaultDomainWithCFGAndDefUseAndSignatureRefinement]")
    assert(domain == "[org.opalj.ai.fpcf.domain.L1DefaultDomainWithCFGAndDefUseAndSignatureRefinement] uses intervals for int values; tracks nullness and must alias information for reference types; records the ai-time def-use information; uses refined signature information")
  }

  def outputDomains(): Unit = {
    DomainRegistry.domainDescriptions.map{ descr =>
      descr
    }.foreach(println(_))
  }
}
