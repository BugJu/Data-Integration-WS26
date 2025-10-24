name := "Aufgabe7"

version := "0.1"

scalaVersion := "2.13.14"

// Test-Abhängigkeiten (z.B. Scalatest für Unit Tests)
libraryDependencies ++= Seq(
  "de.opal-project" % "framework_2.13" % "5.0.0",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.junit.jupiter" % "junit-jupiter-api" % "5.10.3" % Test,
  "com.typesafe.play" %% "play-json" % "2.10.5",
  "org.scala-lang.modules" %% "scala-swing" % "3.0.0",
  "joda-time" % "joda-time" % "2.12.7"
)

dependencyOverrides += "org.scala-lang.modules" %% "scala-xml" % "1.3.0"

mainClass := Some("Main.Main")

// sbt-scoverage Konfiguration für die Codeabdeckung
enablePlugins(scoverage.ScoverageSbtPlugin)
enablePlugins(AssemblyPlugin)

// Optionally configure merging strategies for conflicting files
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs_*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.first
}

Test / parallelExecution := false