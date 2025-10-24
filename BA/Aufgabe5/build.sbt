name := "Aufgabe5"

version := "0.1"

scalaVersion := "2.13.14"

// Test-Abhängigkeiten (z.B. Scalatest für Unit Tests)
libraryDependencies ++= Seq(
  "de.opal-project" % "framework_2.13" % "5.0.0",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.junit.jupiter" % "junit-jupiter-api" % "5.10.3" % Test,
  "joda-time" % "joda-time" % "2.12.7",
  "com.typesafe.play" %% "play-json" % "2.10.5",
  "org.scala-lang.modules" %% "scala-swing" % "3.0.0"
)

dependencyOverrides += "org.scala-lang.modules" %% "scala-xml" % "1.3.0"

// sbt-scoverage Konfiguration für die Codeabdeckung
enablePlugins(scoverage.ScoverageSbtPlugin)

// Corrected coverageReport definition
coverageReport := {
  val targetDir = target.value / "coverage-report"
  coverageReport.value // Remove / Test
  targetDir.mkdirs()
  targetDir
}