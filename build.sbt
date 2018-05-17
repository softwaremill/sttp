// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.{crossProject, CrossType}

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

val commonJSSettings = Seq(
  // https://github.com/scalaz/scalaz/pull/1734#issuecomment-385627061
  scalaJSLinkerConfig ~= {
    _.withBatchMode(System.getenv("CONTINUOUS_INTEGRATION") == "true")
  },
  scalacOptions in Compile += {
    val dir = project.base.toURI.toString.replaceFirst("[^/]+/?$", "")
    val url = "https://raw.githubusercontent.com/softwaremill/sttp"
    s"-P:scalajs:mapSourceURI:$dir->$url/v${version.value}/"
  }
)

val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.1.1"
val akkaStreams = "com.typesafe.akka" %% "akka-stream" % "2.5.12"

val scalaTestVersion = "3.0.5"
val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion

val testServerPort = settingKey[Int]("Port to run the http test server on")
val startTestServer = taskKey[Unit]("Start a http server used by tests")

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(skip in publish := true, name := "sttp")
  .aggregate(rootJVM, rootJS)

lazy val rootJVM = project
  .in(file(".jvm"))
  .settings(commonSettings: _*)
  .settings(skip in publish := true, name := "sttpJVM")
  .aggregate(
    coreJVM,
    catsJVM,
    monixJVM,
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
    circeJVM,
    json4s,
    braveBackend,
    prometheusBackend,
    testsJVM
  )

lazy val rootJS = project
  .in(file(".js"))
  .settings(commonSettings: _*)
  .settings(skip in publish := true, name := "sttpJS")
  .aggregate(coreJS, catsJS, monixJS, circeJS, testsJS)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .settings(commonSettings: _*)
  .jsSettings(commonJSSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test"
    ),
    publishArtifact in Test := true
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.5"
    )
  )
lazy val coreJS = core.js
lazy val coreJVM = core.jvm

//----- implementations
lazy val cats = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("implementations/cats"))
  .settings(commonSettings: _*)
  .jsSettings(commonJSSettings: _*)
  .settings(
    name := "cats",
    libraryDependencies ++= Seq("org.typelevel" %%% "cats-effect" % "1.0.0-RC")
  )
lazy val catsJS = cats.js.dependsOn(coreJS % "compile->compile;test->test")
lazy val catsJVM = cats.jvm.dependsOn(coreJVM % "compile->compile;test->test")

lazy val monix = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("implementations/monix"))
  .settings(commonSettings: _*)
  .jsSettings(commonJSSettings: _*)
  .settings(
    name := "monix",
    libraryDependencies ++= Seq("io.monix" %%% "monix" % "3.0.0-RC1")
  )
lazy val monixJS = monix.js.dependsOn(coreJS % "compile->compile;test->test")
lazy val monixJVM = monix.jvm.dependsOn(coreJVM % "compile->compile;test->test")

lazy val scalaz: Project = (project in file("implementations/scalaz"))
  .settings(commonSettings: _*)
  .settings(
    name := "scalaz",
    libraryDependencies ++= Seq("org.scalaz" %% "scalaz-concurrent" % "7.2.22")
  )
  .dependsOn(coreJVM % "compile->compile;test->test")

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
  .dependsOn(coreJVM % "compile->compile;test->test")

//-- async http client
lazy val asyncHttpClientBackend: Project =
  (project in file("async-http-client-backend"))
    .settings(commonSettings: _*)
    .settings(testServerSettings: _*)
    .settings(
      name := "async-http-client-backend",
      libraryDependencies ++= Seq(
        "org.asynchttpclient" % "async-http-client" % "2.4.7"
      )
    )
    .dependsOn(coreJVM % "compile->compile;test->test")

def asyncHttpClientBackendProject(proj: String): Project = {
  Project(s"asyncHttpClientBackend${proj.capitalize}", file(s"async-http-client-backend/$proj"))
    .settings(commonSettings: _*)
    .settings(testServerSettings: _*)
    .settings(name := s"async-http-client-backend-$proj")
    .dependsOn(asyncHttpClientBackend)
}

lazy val asyncHttpClientFutureBackend: Project =
  asyncHttpClientBackendProject("future")
    .dependsOn(coreJVM % "compile->compile;test->test")

lazy val asyncHttpClientScalazBackend: Project =
  asyncHttpClientBackendProject("scalaz")
    .dependsOn(scalaz % "compile->compile;test->test")

lazy val asyncHttpClientMonixBackend: Project =
  asyncHttpClientBackendProject("monix")
    .dependsOn(monixJVM % "compile->compile;test->test")

lazy val asyncHttpClientCatsBackend: Project =
  asyncHttpClientBackendProject("cats")
    .dependsOn(catsJVM % "compile->compile;test->test")

lazy val asyncHttpClientFs2Backend: Project =
  asyncHttpClientBackendProject("fs2")
    .settings(
      libraryDependencies ++= Seq(
        "com.github.zainab-ali" %% "fs2-reactive-streams" % "0.5.1"
      )
    )
    .dependsOn(coreJVM % "compile->compile;test->test")

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
  .dependsOn(coreJVM % "compile->compile;test->test")

def okhttpBackendProject(proj: String): Project = {
  Project(s"okhttpBackend${proj.capitalize}", file(s"okhttp-backend/$proj"))
    .settings(commonSettings: _*)
    .settings(testServerSettings: _*)
    .settings(name := s"okhttp-backend-$proj")
    .dependsOn(okhttpBackend)
}

lazy val okhttpMonixBackend: Project =
  okhttpBackendProject("monix")
    .dependsOn(monixJVM % "compile->compile;test->test")

lazy val circeVersion = "0.9.3"

//----- json
lazy val circe = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("json/circe"))
  .settings(commonSettings: _*)
  .jsSettings(commonJSSettings: _*)
  .settings(
    name := "circe",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % circeVersion,
      "io.circe" %%% "circe-parser" % circeVersion,
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test"
    )
  )
lazy val circeJS = circe.js.dependsOn(coreJS)
lazy val circeJVM = circe.jvm.dependsOn(coreJVM)

lazy val json4s: Project = (project in file("json/json4s"))
  .settings(commonSettings: _*)
  .settings(
    name := "json4s",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-native" % "3.5.3",
      "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
    )
  )
  .dependsOn(coreJVM)

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
  .dependsOn(coreJVM)

lazy val prometheusBackend: Project = (project in file("metrics/prometheus-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "prometheus-backend",
    libraryDependencies ++= Seq(
      "io.prometheus" % "simpleclient" % "0.4.0",
      scalaTest % "test"
    )
  )
  .dependsOn(coreJVM)

lazy val tests = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("tests"))
  .settings(commonSettings: _*)
  .jsSettings(commonJSSettings: _*)
  .settings(testServerSettings: _*)
  .settings(
    skip in publish := true,
    name := "tests",
    libraryDependencies ++= Seq(
      akkaHttp % "test",
      akkaStreams % "test",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "test"
    )
  )
lazy val testsJS = tests.js.dependsOn(coreJS % "compile->compile;test->test")
lazy val testsJVM = tests.jvm.dependsOn(coreJVM % "compile->compile;test->test")

// https://stackoverflow.com/questions/25766797/how-do-i-start-a-server-before-running-a-test-suite-in-sbt
lazy val testServer: Project = project
  .in(file("test-server"))
  .settings(commonSettings: _*)
  .settings(
    skip in publish := true,
    name := "test-server",
    libraryDependencies ++= Seq(
      akkaHttp,
      akkaStreams
    ),
    mainClass in reStart := Some("com.softwaremill.sttp.server.TestHttpServer"),
    reStartArgs := Seq(s"${testServerPort.value}"),
    testServerPort := 51823,
    startTestServer := (reStart in Test).toTask("").value
  )

// should probably use IntegrationTest instead of Test
lazy val testServerSettings = Seq(
  test in Test := (test in Test).dependsOn(startTestServer in testServer).value,
  testOnly in Test := (testOnly in Test).dependsOn(startTestServer in testServer).evaluated,
  testOptions in Test += Tests.Setup(() => {
    val port = (testServerPort in testServer).value
    PollingUtils.waitUntilServerAvailable(new URL(s"http://localhost:$port"))
  })
)
