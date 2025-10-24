import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json.Json

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

class BenchmarkFrameTest extends AnyFunSuite{

  val fileBenchmark: File = new java.io.File(s"src/test/scala/Test")

  val report: File = new java.io.File(s"src/test/scala/Test/Aufgabe5_l1DefaultSetValuesDomain_1.json")

  val deadCodeReport: DeadCodeReport = Json.parse(new String(Files.readAllBytes(Paths.get(report.toURI)), StandardCharsets.UTF_8)).as[DeadCodeReport]

  val benchmarkFrame: BenchmarkFrame = new BenchmarkFrame(fileBenchmark)

  /** [[BenchmarkFrame.calculateShare]] */
  test("BenchmarkFrame.calculateShare"){
    val share = benchmarkFrame.calculateShare(deadCodeReport.methodsFound)
    assert(share == 16.67)
  }

  /** [[BenchmarkFrame.calculateGroups]] */
  test("BenchmarkFrame.calculateGroup"){
    val grouped = benchmarkFrame.calculateGroups(deadCodeReport.methodsFound, List[String]("INVOKESTATIC", "INVOKEVIRTUAL", "get"))
    assert(grouped.equals(List(1, 1, 2)))
  }
}
