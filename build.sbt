val commonSettings = Seq(
  organization := "com.softwaremill.sttp",
  scalaVersion := "2.12.2",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.8"),
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xlint"),
  scalafmtOnCompile := true,
  scalafmtVersion := "1.0.0",
  // publishing
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),
  publishArtifact in Test := false,
  publishMavenStyle := true,
  scmInfo := Some(
    ScmInfo(url("https://github.com/softwaremill/sttp"),
            "scm:git:git@github.com/softwaremill/sttp.git")),
  developers := List(
    Developer("adamw", "Adam Warski", "", url("https://softwaremill.com"))),
  licenses := ("Apache-2.0",
               url("http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil,
  homepage := Some(url("http://softwaremill.com/open-source")),
  sonatypeProfileName := "com.softwaremill",
  // sbt-release
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseIgnoreUntrackedFiles := true,
  releaseProcess := SttpRelease.steps
)

val akkaHttpVersion = "10.0.9"
val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "sttp")
  .aggregate(core,
             akkaHttpHandler,
             asyncHttpClientHandler,
             futureAsyncHttpClientHandler,
             scalazAsyncHttpClientHandler,
             monixAsyncHttpClientHandler,
             tests)

lazy val core: Project = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % "1.13.5" % "test",
      scalaTest % "test"
    )
  )

lazy val akkaHttpHandler: Project = (project in file("akka-http-handler"))
  .settings(commonSettings: _*)
  .settings(
    name := "akka-http-handler",
    libraryDependencies ++= Seq(
      akkaHttp
    )
  ) dependsOn core

lazy val asyncHttpClientHandler: Project = (project in file(
  "async-http-client-handler"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-handler",
    libraryDependencies ++= Seq(
      "org.asynchttpclient" % "async-http-client" % "2.0.33"
    )
  ) dependsOn core

lazy val futureAsyncHttpClientHandler: Project = (project in file(
  "async-http-client-handler/future"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-handler-future"
  ) dependsOn asyncHttpClientHandler

lazy val scalazAsyncHttpClientHandler: Project = (project in file(
  "async-http-client-handler/scalaz"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-handler-scalaz",
    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-concurrent" % "7.2.14"
    )
  ) dependsOn asyncHttpClientHandler

lazy val monixAsyncHttpClientHandler: Project = (project in file(
  "async-http-client-handler/monix"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-handler-monix",
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % "2.3.0"
    )
  ) dependsOn asyncHttpClientHandler

lazy val tests: Project = (project in file("tests"))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    name := "tests",
    libraryDependencies ++= Seq(
      akkaHttp,
      scalaTest,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "com.github.pathikrit" %% "better-files" % "2.17.1",
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    ).map(_ % "test"),
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % "test"
  ) dependsOn (core, akkaHttpHandler, futureAsyncHttpClientHandler, scalazAsyncHttpClientHandler,
monixAsyncHttpClientHandler)
