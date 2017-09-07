val commonSettings = Seq(
  organization := "com.softwaremill.sttp",
  scalaVersion := "2.12.3",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.11"),
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xlint"),
  scalafmtOnCompile := true,
  scalafmtVersion := "1.1.0",
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
  releaseProcess := SttpRelease.steps,
  // silence transitivie eviction warnings
  evictionWarningOptions in update := EvictionWarningOptions.default
    .withWarnTransitiveEvictions(false)
)

val akkaHttpVersion = "10.0.10"
val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion

val monixVersion = "2.3.0"
val monix = "io.monix" %% "monix" % monixVersion

val circeVersion = "0.8.0"

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "sttp")
  .aggregate(
    core,
    akkaHttpHandler,
    asyncHttpClientHandler,
    asyncHttpClientFutureHandler,
    asyncHttpClientScalazHandler,
    asyncHttpClientMonixHandler,
    asyncHttpClientCatsHandler,
    asyncHttpClientFs2Handler,
    okhttpHandler,
    okhttpMonixHandler,
    circe,
    tests
  )

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
      "org.asynchttpclient" % "async-http-client" % "2.0.35"
    )
  ) dependsOn core

lazy val asyncHttpClientFutureHandler: Project = (project in file(
  "async-http-client-handler/future"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-handler-future"
  ) dependsOn asyncHttpClientHandler

lazy val asyncHttpClientScalazHandler: Project = (project in file(
  "async-http-client-handler/scalaz"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-handler-scalaz",
    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-concurrent" % "7.2.14"
    )
  ) dependsOn asyncHttpClientHandler

lazy val asyncHttpClientMonixHandler: Project = (project in file(
  "async-http-client-handler/monix"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-handler-monix",
    libraryDependencies ++= Seq(monix)
  ) dependsOn asyncHttpClientHandler

lazy val asyncHttpClientCatsHandler: Project = (project in file(
  "async-http-client-handler/cats"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-handler-cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "0.4"
    )
  ) dependsOn asyncHttpClientHandler

lazy val asyncHttpClientFs2Handler: Project = (project in file(
  "async-http-client-handler/fs2"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-handler-fs2",
    libraryDependencies ++= Seq(
      "com.github.zainab-ali" %% "fs2-reactive-streams" % "0.2.2"
    )
  ) dependsOn asyncHttpClientHandler

lazy val okhttpHandler: Project = (project in file("okhttp-handler"))
  .settings(commonSettings: _*)
  .settings(
    name := "okhttp-handler",
    libraryDependencies ++= Seq(
      "com.squareup.okhttp3" % "okhttp" % "3.9.0"
    )
  ) dependsOn core

lazy val okhttpMonixHandler: Project = (project in file("okhttp-handler/monix"))
  .settings(commonSettings: _*)
  .settings(
    name := "okhttp-handler-monix",
    libraryDependencies ++= Seq(monix)
  ) dependsOn okhttpHandler

lazy val circe: Project = (project in file("circe"))
  .settings(commonSettings: _*)
  .settings(
    name := "circe",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      scalaTest % "test"
    )
  ) dependsOn core

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
  ) dependsOn (core, akkaHttpHandler, asyncHttpClientFutureHandler, asyncHttpClientScalazHandler,
asyncHttpClientMonixHandler, asyncHttpClientCatsHandler, asyncHttpClientFs2Handler, okhttpMonixHandler)
