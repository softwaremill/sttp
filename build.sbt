import com.softwaremill.UpdateVersionInDocs
import sbt.Keys.publishArtifact
import sbt.Reference.display
import sbt.internal.ProjectMatrix
// run JS tests inside Chrome, due to jsdom not supporting fetch
import com.softwaremill.SbtSoftwareMillBrowserTestJS._

val scala2_11 = "2.11.12"
val scala2_12 = "2.12.14"
val scala2_13 = "2.13.6"
val scala2 = List(scala2_11, scala2_12, scala2_13)
val scala3 = List("3.0.1")

lazy val testServerPort = settingKey[Int]("Port to run the http test server on")
lazy val startTestServer = taskKey[Unit]("Start a http server used by tests")

// slow down for CI
parallelExecution in Global := false

excludeLintKeys in Global ++= Set(ideSkipProject, reStartArgs)

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp.client3",
  updateDocs := Def.taskDyn {
    val files1 = UpdateVersionInDocs(sLog.value, organization.value, version.value, List(file("README.md")))
    Def.task {
      (docs.jvm(scala2_13) / mdoc).toTask("").value
      files1 ++ Seq(file("generated-docs/out"))
    }
  }.value,
  ideSkipProject := (scalaVersion.value != scala2_13) || thisProjectRef.value.project.contains(
    "JS"
  ) || thisProjectRef.value.project.contains("Native")
)

val commonJvmSettings = commonSettings ++ Seq(
  scalacOptions ++= Seq("-target:jvm-1.8")
)

val commonJsSettings = commonSettings ++ Seq(
  Compile / scalacOptions ++= {
    if (isSnapshot.value) Seq.empty
    else
      Seq {
        val mapSourcePrefix =
          if (ScalaArtifacts.isScala3(scalaVersion.value))
            "-scalajs-mapSourceURI"
          else
            "-P:scalajs:mapSourceURI"
        val dir = project.base.toURI.toString.replaceFirst("[^/]+/?$", "")
        val url = "https://raw.githubusercontent.com/softwaremill/sttp"
        s"$mapSourcePrefix:$dir->$url/v${version.value}/"
      }
  }
)

val commonJsBackendSettings = JSDependenciesPlugin.projectSettings ++ List(
  jsDependencies ++= Seq(
    "org.webjars.npm" % "spark-md5" % "3.0.0" % Test / "spark-md5.js" minified "spark-md5.min.js"
  )
)

val commonNativeSettings = commonSettings ++ Seq(
  nativeLinkStubs := true,
  Test / test := {
    // TODO: re-enable after scala-native release > 0.4.0-M2
    if (sys.env.isDefinedAt("RELEASE_VERSION")) {
      println("[info] Release build, skipping sttp native tests")
    } else { (Test / test).value }
  }
)

// start a test server before running tests of a backend; this is required both for JS tests run inside a
// nodejs/browser environment, as well as for JVM tests where akka-http isn't available (e.g. dotty). To simplify
// things, we always start the test server.
val testServerSettings = Seq(
  Test / test := (Test / test)
    .dependsOn(testServer2_13 / startTestServer)
    .value,
  Test / testOnly := (Test / testOnly)
    .dependsOn(testServer2_13 / startTestServer)
    .evaluated,
  Test / testOptions += Tests.Setup(() => {
    val port = (testServer2_13 / testServerPort).value
    PollingUtils.waitUntilServerAvailable(new URL(s"http://localhost:$port"))
  })
)

val circeVersion: Option[(Long, Long)] => String = {
  case Some((2, 11)) => "0.11.2"
  case _             => "0.14.1"
}
val zioJsonVersion: Option[(Long, Long)] => String = _ => "0.1.5"
val playJsonVersion: Option[(Long, Long)] => String = {
  case Some((2, 11)) => "2.7.4"
  case _             => "2.9.2"
}
val catsEffect_3_version = "3.1.1"
val fs2_3_version = "3.0.6"

val catsEffect_2_version: Option[(Long, Long)] => String = {
  case Some((2, 11)) => "2.0.0"
  case _             => "2.5.1"
}
val fs2_2_version: Option[(Long, Long)] => String = {
  case Some((2, 11)) => "2.1.0"
  case _             => "2.5.6"
}

val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.2.4"
val akkaStreamVersion = "2.6.15"
val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaStreamVersion

val scalaTest = libraryDependencies ++= Seq("freespec", "funsuite", "flatspec", "wordspec", "shouldmatchers").map(m =>
  "org.scalatest" %%% s"scalatest-$m" % "3.2.9" % Test
)

val zioVersion = "1.0.9"
val zioInteropRsVersion = "1.3.5"

val sttpModelVersion = "1.4.7"
val sttpSharedVersion = "1.2.5"

val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

val jeagerClientVersion = "1.6.0"
val braveOpentracingVersion = "1.0.0"
val zipkinSenderOkHttpVersion = "2.16.3"
val resilience4jVersion = "1.7.1"
val http4s_ce2_version = "0.21.24"
val http4s_ce3_version = "0.23.0-RC1"

val compileAndTest = "compile->compile;test->test"

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

lazy val projectsWithOptionalNative: Seq[ProjectReference] = if (sys.env.isDefinedAt("STTP_NATIVE")) {
  println("[info] STTP_NATIVE defined, including sttp-native in the aggregate projects")
  core.projectRefs ++ jsonCommon.projectRefs ++ upickle.projectRefs
} else {
  println("[info] STTP_NATIVE *not* defined, *not* including sttp-native in the aggregate projects")
  scala2.flatMap(v => List[ProjectReference](core.jvm(v), core.js(v), jsonCommon.jvm(v), jsonCommon.js(v))) ++
    scala3.flatMap(v => List[ProjectReference](core.jvm(v), core.js(v))) ++
    List[ProjectReference](
      upickle.jvm(scala2_12),
      upickle.jvm(scala2_13),
      upickle.js(scala2_12),
      upickle.js(scala2_13)
    )
}

lazy val allAggregates = projectsWithOptionalNative ++
  testCompilation.projectRefs ++
  catsCe2.projectRefs ++
  cats.projectRefs ++
  fs2Ce2.projectRefs ++
  fs2.projectRefs ++
  monix.projectRefs ++
  scalaz.projectRefs ++
  zio.projectRefs ++
  akkaHttpBackend.projectRefs ++
  asyncHttpClientBackend.projectRefs ++
  asyncHttpClientFutureBackend.projectRefs ++
  asyncHttpClientScalazBackend.projectRefs ++
  asyncHttpClientZioBackend.projectRefs ++
  asyncHttpClientMonixBackend.projectRefs ++
  asyncHttpClientCatsCe2Backend.projectRefs ++
  asyncHttpClientCatsBackend.projectRefs ++
  asyncHttpClientFs2Ce2Backend.projectRefs ++
  asyncHttpClientFs2Backend.projectRefs ++
  okhttpBackend.projectRefs ++
  okhttpMonixBackend.projectRefs ++
  http4sCe2Backend.projectRefs ++
  http4sBackend.projectRefs ++
  circe.projectRefs ++
  zioJson.projectRefs ++
  json4s.projectRefs ++
  sprayJson.projectRefs ++
  playJson.projectRefs ++
  openTracingBackend.projectRefs ++
  prometheusBackend.projectRefs ++
  zioTelemetryOpenTelemetryBackend.projectRefs ++
  zioTelemetryOpenTracingBackend.projectRefs ++
  httpClientBackend.projectRefs ++
  httpClientMonixBackend.projectRefs ++
  httpClientFs2Ce2Backend.projectRefs ++
  httpClientFs2Backend.projectRefs ++
  httpClientZioBackend.projectRefs ++
  finagleBackend.projectRefs ++
  armeriaBackend.projectRefs ++
  armeriaScalazBackend.projectRefs ++
  armeriaZioBackend.projectRefs ++
  armeriaMonixBackend.projectRefs ++
  armeriaCatsCe2Backend.projectRefs ++
  armeriaCatsBackend.projectRefs ++
  armeriaFs2Ce2Backend.projectRefs ++
  armeriaFs2Backend.projectRefs ++
  scribeBackend.projectRefs ++
  slf4jBackend.projectRefs ++
  examplesCe2.projectRefs ++
  examples.projectRefs ++
  docs.projectRefs

// For CI tests, defining scripts that run JVM/JS/Native tests separately
val testJVM = taskKey[Unit]("Test JVM projects")
val testJS = taskKey[Unit]("Test JS projects")
val testNative = taskKey[Unit]("Test native projects")

def filterProject(p: String => Boolean) =
  ScopeFilter(inProjects(allAggregates.filter(pr => p(display(pr.project))): _*))

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    publish / skip := true,
    name := "sttp",
    testJVM := (Test / test).all(filterProject(p => !p.contains("JS") && !p.contains("Native"))).value,
    testJS := (Test / test).all(filterProject(_.contains("JS"))).value,
    testNative := (Test / test).all(filterProject(_.contains("Native"))).value,
    ideSkipProject := false,
    scalaVersion := scala2_13
  )
  .aggregate(allAggregates: _*)

lazy val testServer = (projectMatrix in file("testing/server"))
  .settings(commonJvmSettings)
  .settings(
    name := "testing-server",
    publish / skip := true,
    libraryDependencies ++= Seq(
      akkaHttp,
      "ch.megard" %% "akka-http-cors" % "0.4.2",
      akkaStreams
    ),
    // the test server needs to be started before running any backend tests
    reStart / mainClass := Some("sttp.client3.testing.server.HttpServer"),
    reStart / reStartArgs := Seq(s"${(Test / testServerPort).value}"),
    reStart / fullClasspath := (Test / fullClasspath).value,
    testServerPort := 51823,
    startTestServer := reStart.toTask("").value
  )
  .jvmPlatform(scalaVersions = List(scala2_13))

lazy val testServer2_13 = testServer.jvm(scala2_13)

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.model" %%% "core" % sttpModelVersion,
      "com.softwaremill.sttp.shared" %%% "core" % sttpSharedVersion,
      "com.softwaremill.sttp.shared" %%% "ws" % sttpSharedVersion
    ),
    scalaTest
  )
  .settings(testServerSettings)
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3,
    settings = {
      commonJvmSettings ++ List(
        Test / publishArtifact := true // allow implementations outside of this repo
      )
    }
  )
  .jsPlatform(
    scalaVersions = scala2 ++ scala3,
    settings = {
      commonJsSettings ++ commonJsBackendSettings ++ browserChromeTestSettings ++ List(
        Test / publishArtifact := true
      )
    }
  )
  .nativePlatform(
    scalaVersions = scala2,
    settings = {
      commonNativeSettings ++ List(
        Test / publishArtifact := true
      )
    }
  )

lazy val testCompilation = (projectMatrix in file("testing/compile"))
  .settings(commonJvmSettings)
  .settings(
    name := "testing-compile",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % Test
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = List(scala2_13))
  .dependsOn(core % Test)

//----- effects
lazy val catsCe2 = (projectMatrix in file("effects/cats-ce2"))
  .settings(
    name := "catsCe2",
    Test / publishArtifact := true,
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "org.typelevel" %%% "cats-effect" % catsEffect_2_version(_)
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3,
    settings = commonJvmSettings
  )
  .jsPlatform(
    scalaVersions = List(scala2_12, scala2_13) ++ scala3,
    settings = commonJsSettings ++ commonJsBackendSettings ++ browserChromeTestSettings ++ testServerSettings
  )

lazy val cats = (projectMatrix in file("effects/cats"))
  .settings(
    name := "cats",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect-kernel" % catsEffect_3_version,
      "org.typelevel" %%% "cats-effect" % catsEffect_3_version % Test
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = List(scala2_12, scala2_13) ++ scala3,
    settings = commonJvmSettings
  )
  .jsPlatform(
    scalaVersions = List(scala2_12, scala2_13) ++ scala3,
    settings = commonJsSettings ++ commonJsBackendSettings ++ browserChromeTestSettings ++ testServerSettings
  )

lazy val fs2Ce2 = (projectMatrix in file("effects/fs2-ce2"))
  .settings(
    name := "fs2Ce2",
    Test / publishArtifact := true,
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "co.fs2" %%% "fs2-core" % fs2_2_version(_)
    ),
    libraryDependencies += "com.softwaremill.sttp.shared" %% "fs2-ce2" % sttpSharedVersion
  )
  .dependsOn(core % compileAndTest, catsCe2 % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = List(scala2_12, scala2_13) ++ scala3, settings = commonJsSettings)

lazy val fs2 = (projectMatrix in file("effects/fs2"))
  .settings(
    name := "fs2",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % fs2_3_version,
      "com.softwaremill.sttp.shared" %% "fs2" % sttpSharedVersion
    )
  )
  .dependsOn(core % compileAndTest, cats % compileAndTest)
  .jvmPlatform(
    scalaVersions = List(scala2_12, scala2_13) ++ scala3,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = List(scala2_12, scala2_13) ++ scala3, settings = commonJsSettings)

lazy val monix = (projectMatrix in file("effects/monix"))
  .settings(
    name := "monix",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq(
      "io.monix" %%% "monix" % "3.4.0",
      "com.softwaremill.sttp.shared" %%% "monix" % sttpSharedVersion
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = List(scala2_12, scala2_13),
    settings = commonJvmSettings ++ List(
      libraryDependencies ++= Seq("io.monix" %% "monix-nio" % "0.0.9")
    )
  )
  .jsPlatform(
    scalaVersions = List(scala2_12, scala2_13) ++ scala3,
    settings = commonJsSettings ++ commonJsBackendSettings ++ browserChromeTestSettings ++ testServerSettings
  )

lazy val zio = (projectMatrix in file("effects/zio"))
  .settings(commonJvmSettings)
  .settings(
    name := "zio",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio" % zioVersion,
      "com.softwaremill.sttp.shared" %% "zio" % sttpSharedVersion
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )
  .jsPlatform(
    scalaVersions = List(scala2_12, scala2_13) ++ scala3,
    settings = commonJsSettings ++ commonJsBackendSettings ++ browserChromeTestSettings ++ testServerSettings
  )

lazy val scalaz = (projectMatrix in file("effects/scalaz"))
  .settings(commonJvmSettings)
  .settings(
    name := "scalaz",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq("org.scalaz" %% "scalaz-concurrent" % "7.2.33")
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2
  )

//----- backends
//-- akka
lazy val akkaHttpBackend = (projectMatrix in file("akka-http-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "akka-http-backend",
    libraryDependencies ++= Seq(
      akkaHttp,
      // provided as we don't want to create a transitive dependency on a specific streams version,
      // just as akka-http doesn't
      akkaStreams % "provided",
      "com.softwaremill.sttp.shared" %% "akka" % sttpSharedVersion
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = List(scala2_12, scala2_13)
  )

//-- async http client
lazy val asyncHttpClientBackend = (projectMatrix in file("async-http-client-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "async-http-client-backend",
    libraryDependencies ++= Seq(
      "org.asynchttpclient" % "async-http-client" % "2.12.3"
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3
  )

def asyncHttpClientBackendProject(proj: String, includeDotty: Boolean = false, include2_11: Boolean = true) = {
  ProjectMatrix(s"asyncHttpClientBackend${proj.capitalize}", file(s"async-http-client-backend/$proj"))
    .settings(commonJvmSettings)
    .settings(testServerSettings)
    .settings(name := s"async-http-client-backend-$proj")
    .dependsOn(asyncHttpClientBackend % compileAndTest)
    .jvmPlatform(
      scalaVersions =
        (if (include2_11) List(scala2_11) else Nil) ++ List(scala2_12, scala2_13) ++ (if (includeDotty) scala3 else Nil)
    )
}

lazy val asyncHttpClientFutureBackend =
  asyncHttpClientBackendProject("future", includeDotty = true)
    .dependsOn(core % compileAndTest)

lazy val asyncHttpClientScalazBackend =
  asyncHttpClientBackendProject("scalaz")
    .dependsOn(scalaz % compileAndTest)

lazy val asyncHttpClientZioBackend =
  asyncHttpClientBackendProject("zio", includeDotty = true)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-interop-reactivestreams" % zioInteropRsVersion
      )
    )
    .dependsOn(zio % compileAndTest)

lazy val asyncHttpClientMonixBackend =
  asyncHttpClientBackendProject("monix", includeDotty = false, include2_11 = false)
    .dependsOn(monix % compileAndTest)

lazy val asyncHttpClientCatsCe2Backend =
  asyncHttpClientBackendProject("cats-ce2", includeDotty = true)
    .dependsOn(catsCe2 % compileAndTest)

lazy val asyncHttpClientCatsBackend =
  asyncHttpClientBackendProject("cats", includeDotty = true, include2_11 = false)
    .dependsOn(cats % compileAndTest)

lazy val asyncHttpClientFs2Ce2Backend =
  asyncHttpClientBackendProject("fs2-ce2", includeDotty = true)
    .settings(
      libraryDependencies ++= dependenciesFor(scalaVersion.value)(
        "co.fs2" %% "fs2-reactive-streams" % fs2_2_version(_),
        "co.fs2" %% "fs2-io" % fs2_2_version(_)
      )
    )
    .dependsOn(catsCe2 % compileAndTest)
    .dependsOn(fs2Ce2 % compileAndTest)

lazy val asyncHttpClientFs2Backend =
  asyncHttpClientBackendProject("fs2", includeDotty = true, include2_11 = false)
    .settings(
      libraryDependencies ++= Seq(
        "co.fs2" %% "fs2-reactive-streams" % fs2_3_version,
        "co.fs2" %% "fs2-io" % fs2_3_version
      )
    )
    .dependsOn(cats % compileAndTest)
    .dependsOn(fs2 % compileAndTest)

//-- okhttp
lazy val okhttpBackend = (projectMatrix in file("okhttp-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "okhttp-backend",
    libraryDependencies ++= Seq(
      "com.squareup.okhttp3" % "okhttp" % "4.9.1"
    )
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(core % compileAndTest)

def okhttpBackendProject(proj: String, includeDotty: Boolean) = {
  ProjectMatrix(s"okhttpBackend${proj.capitalize}", file(s"okhttp-backend/$proj"))
    .settings(commonJvmSettings)
    .settings(testServerSettings)
    .settings(name := s"okhttp-backend-$proj")
    .jvmPlatform(scalaVersions = List(scala2_12, scala2_13) ++ (if (includeDotty) scala3 else Nil))
    .dependsOn(okhttpBackend)
}

lazy val okhttpMonixBackend =
  okhttpBackendProject("monix", includeDotty = false)
    .dependsOn(monix % compileAndTest)

//-- http4s
lazy val http4sCe2Backend = (projectMatrix in file("http4s-ce2-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "http4s-ce2-backend",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-client" % http4s_ce2_version,
      "org.http4s" %% "http4s-blaze-client" % http4s_ce2_version % Optional
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13))
  .dependsOn(catsCe2 % compileAndTest, core % compileAndTest, fs2Ce2 % compileAndTest)

lazy val http4sBackend = (projectMatrix in file("http4s-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "http4s-backend",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-client" % http4s_ce3_version,
      "org.http4s" %% "http4s-blaze-client" % http4s_ce3_version % Optional
    ),
    evictionErrorLevel := Level.Info
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13) ++ scala3)
  .dependsOn(cats % compileAndTest, core % compileAndTest, fs2 % compileAndTest)

//-- httpclient-java11
lazy val httpClientBackend = (projectMatrix in file("httpclient-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "httpclient-backend",
    scalacOptions ++= Seq("-J--add-modules", "-Jjava.net.http"),
    scalacOptions ++= {
      if (scalaVersion.value == scala2_13) List("-target:jvm-11") else Nil
    },
    libraryDependencies += "org.reactivestreams" % "reactive-streams-flow-adapters" % "1.0.2"
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13) ++ scala3)
  .dependsOn(core % compileAndTest)

def httpClientBackendProject(proj: String, includeDotty: Boolean = false) = {
  ProjectMatrix(s"httpClientBackend${proj.capitalize}", file(s"httpclient-backend/$proj"))
    .settings(commonJvmSettings)
    .settings(testServerSettings)
    .settings(name := s"httpclient-backend-$proj")
    .jvmPlatform(
      scalaVersions = List(scala2_12, scala2_13) ++ (if (includeDotty) scala3 else Nil)
    )
    .dependsOn(httpClientBackend % compileAndTest)
}

lazy val httpClientMonixBackend =
  httpClientBackendProject("monix", includeDotty = false)
    .dependsOn(monix % compileAndTest)

lazy val httpClientFs2Ce2Backend =
  httpClientBackendProject("fs2-ce2", includeDotty = true)
    .settings(
      libraryDependencies ++= dependenciesFor(scalaVersion.value)(
        "co.fs2" %% "fs2-reactive-streams" % fs2_2_version(_),
        "co.fs2" %% "fs2-io" % fs2_2_version(_)
      )
    )
    .dependsOn(fs2Ce2 % compileAndTest)

lazy val httpClientFs2Backend =
  httpClientBackendProject("fs2", includeDotty = true)
    .settings(
      libraryDependencies ++= Seq(
        "co.fs2" %% "fs2-reactive-streams" % fs2_3_version,
        "co.fs2" %% "fs2-io" % fs2_3_version
      )
    )
    .dependsOn(fs2 % compileAndTest)

lazy val httpClientZioBackend =
  httpClientBackendProject("zio", includeDotty = true)
    .settings(
      libraryDependencies ++=
        Seq(
          "dev.zio" %% "zio-interop-reactivestreams" % zioInteropRsVersion,
          "dev.zio" %% "zio-nio" % "1.0.0-RC11"
        )
    )
    .dependsOn(zio % compileAndTest)

//-- finagle backend
lazy val finagleBackend = (projectMatrix in file("finagle-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "finagle-backend",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-http" % "21.6.0"
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13))
  .dependsOn(core % compileAndTest)

lazy val armeriaBackend = (projectMatrix in file("armeria-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "armeria-backend",
    libraryDependencies += "com.linecorp.armeria" % "armeria" % "1.9.2"
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13) ++ scala3)
  .dependsOn(core % compileAndTest)

def armeriaBackendProject(proj: String, includeDotty: Boolean = false) = {
  ProjectMatrix(s"armeriaBackend${proj.capitalize}", file(s"armeria-backend/$proj"))
    .settings(commonJvmSettings)
    .settings(testServerSettings)
    .settings(name := s"armeria-backend-$proj")
    .dependsOn(armeriaBackend % compileAndTest)
    .jvmPlatform(
      scalaVersions = List(scala2_12, scala2_13) ++ (if (includeDotty) scala3 else Nil)
    )
}

lazy val armeriaMonixBackend =
  armeriaBackendProject("monix")
    .dependsOn(monix % compileAndTest)

lazy val armeriaFs2Ce2Backend =
  armeriaBackendProject("fs2-ce2")
    .settings(
      libraryDependencies ++= dependenciesFor(scalaVersion.value)(
        "co.fs2" %% "fs2-reactive-streams" % fs2_2_version(_)
      )
    )
    .dependsOn(fs2Ce2 % compileAndTest)

lazy val armeriaFs2Backend =
  armeriaBackendProject("fs2")
    .settings(
      libraryDependencies ++= Seq(
        "co.fs2" %% "fs2-reactive-streams" % fs2_3_version
      )
    )
    .dependsOn(fs2 % compileAndTest)

lazy val armeriaCatsCe2Backend =
  armeriaBackendProject("cats-ce2")
    .dependsOn(catsCe2 % compileAndTest)

lazy val armeriaCatsBackend =
  armeriaBackendProject("cats", includeDotty = true)
    .dependsOn(cats % compileAndTest)

lazy val armeriaScalazBackend =
  armeriaBackendProject("scalaz")
    .dependsOn(scalaz % compileAndTest)

lazy val armeriaZioBackend =
  armeriaBackendProject("zio", includeDotty = true)
    .settings(
      libraryDependencies ++= Seq("dev.zio" %% "zio-interop-reactivestreams" % zioInteropRsVersion)
    )
    .dependsOn(zio % compileAndTest)

//----- json
lazy val jsonCommon = (projectMatrix in (file("json/common")))
  .settings(
    name := "json-common"
  )
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = scala2 ++ scala3, settings = commonJsSettings)
  .nativePlatform(scalaVersions = scala2, settings = commonNativeSettings)
  .dependsOn(core)

lazy val circe = (projectMatrix in file("json/circe"))
  .settings(
    name := "circe",
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "io.circe" %%% "circe-core" % circeVersion(_),
      "io.circe" %%% "circe-parser" % circeVersion(_),
      "io.circe" %%% "circe-generic" % circeVersion(_) % Test
    ),
    scalaTest,
    Compile / unmanagedSourceDirectories := {
      val current = (Compile / unmanagedSourceDirectories).value
      val sv = (Compile / scalaVersion).value
      val baseDirectory = (Compile / scalaSource).value
      val suffixes = CrossVersion.partialVersion(sv) match {
        case Some((2, 11)) => List("2.11")
        case _             => List("2.12+")
      }
      val versionSpecificSources = suffixes.map(s => new File(baseDirectory.getAbsolutePath + "-" + s))
      versionSpecificSources ++ current
    }
  )
  .jvmPlatform(
    scalaVersions = scala2 ++ scala3,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = List(scala2_12, scala2_13) ++ scala3, settings = commonJsSettings)
  .dependsOn(core, jsonCommon)

lazy val zioJson = (projectMatrix in file("json/zio-json"))
  .settings(
    name := "zio-json",
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "dev.zio" %%% "zio-json" % zioJsonVersion(_)
    ) ++ Seq("com.softwaremill.sttp.shared" %%% "zio" % sttpSharedVersion),
    scalaTest
  )
  .jvmPlatform(
    scalaVersions = Seq(scala2_12, scala2_13),
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = List(scala2_12, scala2_13), settings = commonJsSettings)
  .dependsOn(core, jsonCommon)

lazy val upickle = (projectMatrix in file("json/upickle"))
  .settings(
    name := "upickle",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "upickle" % "1.4.0"
    ),
    scalaTest,
    // using macroRW causes a "match may not be exhaustive" error
    Test / scalacOptions --= Seq("-Wconf:cat=other-match-analysis:error")
  )
  .jvmPlatform(
    scalaVersions = List(scala2_12, scala2_13),
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = List(scala2_12, scala2_13), settings = commonJsSettings)
  .nativePlatform(scalaVersions = List(scala2_12, scala2_13), settings = commonNativeSettings)
  .dependsOn(core, jsonCommon)

lazy val json4sVersion = "4.0.1"

lazy val json4s = (projectMatrix in file("json/json4s"))
  .settings(commonJvmSettings)
  .settings(
    name := "json4s",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-core" % json4sVersion,
      "org.json4s" %% "json4s-native" % json4sVersion % Test
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(core, jsonCommon)

lazy val sprayJson = (projectMatrix in file("json/spray-json"))
  .settings(commonJvmSettings)
  .settings(
    name := "spray-json",
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-json" % "1.3.6"
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(core, jsonCommon)

lazy val playJson = (projectMatrix in file("json/play-json"))
  .settings(
    name := "play-json",
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "com.typesafe.play" %%% "play-json" % playJsonVersion(_)
    ),
    scalaTest
  )
  .jvmPlatform(
    scalaVersions = scala2,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = List(scala2_12, scala2_13), settings = commonJsSettings)
  .dependsOn(core, jsonCommon)

lazy val openTracingBackend = (projectMatrix in file("metrics/open-tracing-backend"))
  .settings(commonJvmSettings)
  .settings(
    name := "opentracing-backend",
    libraryDependencies ++= Seq(
      "io.opentracing" % "opentracing-api" % "0.33.0",
      "io.opentracing" % "opentracing-mock" % "0.33.0" % Test
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = scala2 ++ scala3)
  .dependsOn(core)

lazy val prometheusBackend = (projectMatrix in file("metrics/prometheus-backend"))
  .settings(commonJvmSettings)
  .settings(
    name := "prometheus-backend",
    libraryDependencies ++= Seq(
      "io.prometheus" % "simpleclient" % "0.11.0"
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = scala2 ++ scala3)
  .dependsOn(core)

lazy val zioTelemetryOpenTelemetryBackend = (projectMatrix in file("metrics/zio-telemetry-open-telemetry-backend"))
  .settings(commonJvmSettings)
  .settings(
    name := "zio-telemetry-opentelemetry-backend",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-opentelemetry" % "0.8.1",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0",
      "io.opentelemetry" % "opentelemetry-sdk-testing" % "1.4.0" % Test
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13))
  .dependsOn(zio % compileAndTest)
  .dependsOn(core)

lazy val zioTelemetryOpenTracingBackend = (projectMatrix in file("metrics/zio-telemetry-open-tracing-backend"))
  .settings(commonJvmSettings)
  .settings(
    name := "zio-telemetry-opentracing-backend",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-opentracing" % "0.8.1",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0"
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13))
  .dependsOn(zio % compileAndTest)
  .dependsOn(core)

lazy val scribeBackend = (projectMatrix in file("logging/scribe"))
  .settings(commonJvmSettings)
  .settings(
    name := "scribe-backend",
    libraryDependencies ++= Seq(
      "com.outr" %%% "scribe" % "3.5.5"
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13))
  .dependsOn(core)

lazy val slf4jBackend = (projectMatrix in file("logging/slf4j"))
  .settings(commonJvmSettings)
  .settings(
    name := "slf4j-backend",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "1.7.31"
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = scala2 ++ scala3)
  .dependsOn(core)

lazy val examplesCe2 = (projectMatrix in file("examples-ce2"))
  .settings(commonJvmSettings)
  .settings(
    name := "examples-ce2",
    publish / skip := true,
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "io.circe" %% "circe-generic" % circeVersion(_)
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_13))
  .dependsOn(
    circe,
    asyncHttpClientMonixBackend
  )

lazy val examples = (projectMatrix in file("examples"))
  .settings(commonJvmSettings)
  .settings(
    name := "examples",
    publish / skip := true,
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "io.circe" %% "circe-generic" % circeVersion(_),
      _ => "org.json4s" %% "json4s-native" % json4sVersion,
      _ => akkaStreams,
      _ => logback
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13))
  .dependsOn(
    core,
    asyncHttpClientZioBackend,
    akkaHttpBackend,
    asyncHttpClientFs2Backend,
    json4s,
    circe,
    scribeBackend,
    slf4jBackend
  )

//TODO this should be invoked by compilation process, see #https://github.com/scalameta/mdoc/issues/355
val compileDocs: TaskKey[Unit] = taskKey[Unit]("Compiles docs module throwing away its output")
compileDocs := {
  (docs.jvm(scala2_13) / mdoc).toTask(" --out target/sttp-docs").value
}

lazy val docs: ProjectMatrix = (projectMatrix in file("generated-docs")) // important: it must not be docs/
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    mdocIn := file("docs"),
    moduleName := "sttp-docs",
    mdocVariables := Map(
      "VERSION" -> version.value,
      "JEAGER_CLIENT_VERSION" -> jeagerClientVersion,
      "BRAVE_OPENTRACING_VERSION" -> braveOpentracingVersion,
      "ZIPKIN_SENDER_OKHTTP_VERSION" -> zipkinSenderOkHttpVersion,
      "AKKA_STREAM_VERSION" -> akkaStreamVersion,
      "CIRCE_VERSION" -> circeVersion(None)
    ),
    mdocOut := file("generated-docs/out"),
    publishArtifact := false,
    name := "docs",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-native" % json4sVersion,
      "io.circe" %% "circe-generic" % "0.14.1",
      "commons-io" % "commons-io" % "2.10.0",
      "io.github.resilience4j" % "resilience4j-circuitbreaker" % resilience4jVersion,
      "io.github.resilience4j" % "resilience4j-ratelimiter" % resilience4jVersion,
      "io.jaegertracing" % "jaeger-client" % jeagerClientVersion,
      "io.opentracing.brave" % "brave-opentracing" % braveOpentracingVersion,
      "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % zipkinSenderOkHttpVersion,
      "io.opentelemetry" % "opentelemetry-semconv" % "1.2.0-alpha",
      akkaStreams
    ),
    evictionErrorLevel := Level.Info
  )
  .dependsOn(
    core % "compile->test",
    akkaHttpBackend,
    json4s,
    circe,
    sprayJson,
    zioJson,
    asyncHttpClientZioBackend,
    //asyncHttpClientMonixBackend, // monix backends are commented out because they depend on cats-effect2
    asyncHttpClientFs2Backend,
    asyncHttpClientCatsBackend,
    asyncHttpClientFutureBackend,
    asyncHttpClientScalazBackend,
    armeriaZioBackend,
    //armeriaMonixBackend,
    armeriaFs2Backend,
    armeriaCatsBackend,
    armeriaScalazBackend,
    okhttpBackend,
    //okhttpMonixBackend,
    httpClientBackend,
    httpClientFs2Backend,
    http4sBackend,
    //httpClientMonixBackend,
    httpClientZioBackend,
    openTracingBackend,
    prometheusBackend,
    slf4jBackend,
    zioTelemetryOpenTelemetryBackend,
    zioTelemetryOpenTracingBackend
  )
  .jvmPlatform(scalaVersions = List(scala2_13))
