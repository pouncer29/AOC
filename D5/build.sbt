ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "D5"
  )

val libraryDependencies = Seq("org.slf4j" % "slf4j-api" % "2.0.5",
"org.slf4j" % "slf4j-simple" % "2.0.5")