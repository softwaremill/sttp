val commonSettings = Seq(
  organization := "com.softwaremill.sttp",
  scalaVersion := "2.12.6",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.12"),
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xlint"),
  scalafmtOnCompile := true,
  scalafmtVersion := "1.4.0",
  // publishing
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),
  publishArtifact in Test := false,
  publishMavenStyle := true,
  scmInfo := Some(ScmInfo(url("https://github.com/softwaremill/sttp"), "scm:git:git@github.com/softwaremill/sttp.git")),
  developers := List(Developer("adamw", "Adam Warski", "", url("https://softwaremill.com"))),
  licenses := ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil,
  homepage := Some(url("http://softwaremill.com/open-source")),
  sonatypeProfileName := "com.softwaremill",
  // sbt-release
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseIgnoreUntrackedFiles := true,
  releaseProcess := SttpRelease.steps,
  // silence transitive eviction warnings
  evictionWarningOptions in update := EvictionWarningOptions.default
    .withWarnTransitiveEvictions(false)
)

val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.1.1"
val akkaStreams = "com.typesafe.akka" %% "akka-stream" % "2.5.12"

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"

val testServerPort = settingKey[Int]("Port to run the http test server on")
val startTestServer = taskKey[Unit]("Start a http server used by tests")

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(skip in publish := true, name := "sttp")
  .aggregate(
    core,
    cats,
    monix,
    scalaz,
    akkaHttpBackend,
    asyncHttpClientBackend,
    asyncHttpClientFutureBackend,
    asyncHttpClientScalazBackend,
    asyncHttpClientMonixBackend,
    asyncHttpClientCatsBackend,
    asyncHttpClientFs2Backend,
    okhttpBackend,
    okhttpMonixBackend,
    circe,
    json4s,
    braveBackend,
    prometheusBackend,
    testServer
  )

lazy val core: Project = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(testServerSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.github.pathikrit" %% "better-files" % "3.4.0" % "test",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "test",
      scalaTest % "test"
    ),
    publishArtifact in Test := true // allow implementations outside of this repo
  )

//----- implementations
lazy val cats: Project = (project in file("implementations/cats"))
  .settings(commonSettings: _*)
  .settings(
    name := "cats",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq("org.typelevel" %% "cats-effect" % "1.0.0-RC")
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val monix: Project = (project in file("implementations/monix"))
  .settings(commonSettings: _*)
  .settings(
    name := "monix",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq("io.monix" %% "monix" % "3.0.0-RC1")
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val scalaz: Project = (project in file("implementations/scalaz"))
  .settings(commonSettings: _*)
  .settings(
    name := "scalaz",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq("org.scalaz" %% "scalaz-concurrent" % "7.2.22")
  )
  .dependsOn(core % "compile->compile;test->test")

//----- backends
//-- akka
lazy val akkaHttpBackend: Project = (project in file("akka-http-backend"))
  .settings(commonSettings: _*)
  .settings(testServerSettings: _*)
  .settings(
    name := "akka-http-backend",
    libraryDependencies ++= Seq(
      akkaHttp,
      // provided as we don't want to create a transitive dependency on a specific streams version,
      // just as akka-http doesn't
      akkaStreams % "provided"
    )
  )
  .dependsOn(core % "compile->compile;test->test")

//-- async http client
lazy val asyncHttpClientBackend: Project = {
  (project in file("async-http-client-backend"))
    .settings(commonSettings: _*)
    .settings(testServerSettings: _*)
    .settings(
      name := "async-http-client-backend",
      libraryDependencies ++= Seq(
        "org.asynchttpclient" % "async-http-client" % "2.4.7"
      )
    )
    .dependsOn(core % "compile->compile;test->test")
}

def asyncHttpClientBackendProject(proj: String): Project = {
  Project(s"asyncHttpClientBackend${proj.capitalize}", file(s"async-http-client-backend/$proj"))
    .settings(commonSettings: _*)
    .settings(testServerSettings: _*)
    .settings(name := s"async-http-client-backend-$proj")
    .dependsOn(asyncHttpClientBackend)
}

lazy val asyncHttpClientFutureBackend: Project =
  asyncHttpClientBackendProject("future")
    .dependsOn(core % "compile->compile;test->test")

lazy val asyncHttpClientScalazBackend: Project =
  asyncHttpClientBackendProject("scalaz")
    .dependsOn(scalaz % "compile->compile;test->test")

lazy val asyncHttpClientMonixBackend: Project =
  asyncHttpClientBackendProject("monix")
    .dependsOn(monix % "compile->compile;test->test")

lazy val asyncHttpClientCatsBackend: Project =
  asyncHttpClientBackendProject("cats")
    .dependsOn(cats % "compile->compile;test->test")

lazy val asyncHttpClientFs2Backend: Project =
  asyncHttpClientBackendProject("fs2")
    .settings(
      libraryDependencies ++= Seq(
        "com.github.zainab-ali" %% "fs2-reactive-streams" % "0.5.1"
      )
    )
    .dependsOn(core % "compile->compile;test->test")

//-- okhttp
lazy val okhttpBackend: Project = (project in file("okhttp-backend"))
  .settings(commonSettings: _*)
  .settings(testServerSettings: _*)
  .settings(
    name := "okhttp-backend",
    libraryDependencies ++= Seq(
      "com.squareup.okhttp3" % "okhttp" % "3.10.0"
    )
  )
  .dependsOn(core % "compile->compile;test->test")

def okhttpBackendProject(proj: String): Project = {
  Project(s"okhttpBackend${proj.capitalize}", file(s"okhttp-backend/$proj"))
    .settings(commonSettings: _*)
    .settings(testServerSettings: _*)
    .settings(name := s"okhttp-backend-$proj")
    .dependsOn(okhttpBackend)
}

lazy val okhttpMonixBackend: Project =
  okhttpBackendProject("monix")
    .dependsOn(monix % "compile->compile;test->test")

lazy val circeVersion = "0.9.3"

//----- json
lazy val circe: Project = (project in file("json/circe"))
  .settings(commonSettings: _*)
  .settings(
    name := "circe",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      scalaTest % "test"
    )
  ) dependsOn core

lazy val json4s: Project = (project in file("json/json4s"))
  .settings(commonSettings: _*)
  .settings(
    name := "json4s",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-native" % "3.5.3",
      scalaTest % "test"
    )
  ) dependsOn core

lazy val braveVersion = "4.19.2"

lazy val braveBackend: Project = (project in file("metrics/brave-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "brave-backend",
    libraryDependencies ++= Seq(
      "io.zipkin.brave" % "brave" % braveVersion,
      "io.zipkin.brave" % "brave-instrumentation-http" % braveVersion,
      "io.zipkin.brave" % "brave-instrumentation-http-tests" % braveVersion % "test",
      scalaTest % "test"
    )
  )
  .dependsOn(core)

lazy val prometheusBackend: Project = (project in file("metrics/prometheus-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "prometheus-backend",
    libraryDependencies ++= Seq(
      "io.prometheus" % "simpleclient" % "0.4.0",
      scalaTest % "test"
    )
  )
  .dependsOn(core)

// https://stackoverflow.com/questions/25766797/how-do-i-start-a-server-before-running-a-test-suite-in-sbt
lazy val testServer: Project = project
  .in(file("test-server"))
  .settings(commonSettings: _*)
  .settings(
    name := "test-server",
    libraryDependencies ++= Seq(akkaHttp, akkaStreams),
    mainClass in reStart := Some("com.softwaremill.sttp.server.TestHttpServer"),
    reStartArgs := Seq(s"${testServerPort.value}"),
    testServerPort := 51823,
    startTestServer := (reStart in Test).toTask("").value
  )

lazy val testServerSettings = Seq(
  test in Test := (test in Test).dependsOn(startTestServer in testServer).value,
  testOnly in Test := (testOnly in Test).dependsOn(startTestServer in testServer).evaluated,
  testOptions in Test += Tests.Setup(() => {
    val port = (testServerPort in testServer).value
    PollingUtils.waitUntilServerAvailable(new URL(s"http://localhost:$port"))
  })
)
