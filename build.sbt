import com.softwaremill.Publish.{ossPublishSettings, updateDocs}
import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import com.softwaremill.UpdateVersionInDocs
import sbt.Keys.publishArtifact
import sbt.Reference.display
import sbt.internal.ProjectMatrix
import complete.DefaultParsers._
// run JS tests inside Chrome, due to jsdom not supporting fetch
import com.softwaremill.SbtSoftwareMillBrowserTestJS._

val scala2_12 = "2.12.20"
val scala2_13 = "2.13.16"
val scala3 = "3.3.6"

val scala2 = List(scala2_12, scala2_13)
val scala2And3 = scala2 ++ List(scala3)
val scala2_13And3 = List(scala2_13, scala3)

val examplesScalaVersion = scala3
val documentationScalaVersion = scala3
val ideScalaVersion = scala3

lazy val testServerPort = settingKey[Int]("Port to run the http test server on")
lazy val startTestServer = taskKey[Unit]("Start a http server used by tests")
lazy val verifyExamplesCompileUsingScalaCli = taskKey[Unit]("Verify that each example compiles using Scala CLI")

// slow down for CI
parallelExecution in Global := false
concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

excludeLintKeys in Global ++= Set(ideSkipProject, reStartArgs)
val scopesDescription = "Scala version can be: 2.12, 2.13, 3; platform: JVM, JS, Native"
val compileScoped =
  inputKey[Unit](
    s"Compiles sources in the given scope. Usage: compileScoped [scala version] [platform]. $scopesDescription"
  )
val testScoped =
  inputKey[Unit](s"Run tests in the given scope. Usage: testScoped [scala version] [platform]. $scopesDescription")

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp.client4",
  ideSkipProject := (scalaVersion.value != ideScalaVersion)
    || thisProjectRef.value.project.contains("JS") || thisProjectRef.value.project.contains("Native"),
  bspEnabled := !ideSkipProject.value,
  mimaPreviousArtifacts := Set.empty // we only use MiMa for `core` for now, using enableMimaSettings
)

val commonJvmSettings = commonSettings ++ Seq(
  Test / testOptions += Tests.Argument("-oD") // add test timings; js build specify other options which conflict
)

val commonJsSettings = commonSettings ++ Seq(
  scalaJSLinkerConfig ~= {
    _.withBatchMode(true).withParallel(false)
  },
  libraryDependencies += ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13),
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
    "org.webjars.npm" % "spark-md5" % "3.0.2" % Test / "spark-md5.js" minified "spark-md5.min.js"
  )
)

val commonNativeSettings = commonSettings ++ Seq(
  Test / test := {
    // TODO: re-enable after scala-native release > 0.4.0-M2
    if (sys.env.isDefinedAt("RELEASE_VERSION")) {
      println("[info] Release build, skipping sttp native tests")
    } else { (Test / test).value }
  }
)

val versioningSchemeSettings = Seq(versionScheme := Some("early-semver"))

val enableMimaSettings = Seq(
  mimaPreviousArtifacts := {
    val current = version.value
    val isRcOrMilestone = current.contains("M") || current.contains("RC")
    if (!isRcOrMilestone) {
      val previous = previousStableVersion.value
      println(s"[info] Not a M or RC version, using previous version for MiMa check: $previous")
      previousStableVersion.value.map(organization.value %% moduleName.value % _).toSet
    } else {
      println(s"[info] $current is an M or RC version, no previous version to check with MiMa")
      Set.empty
    }
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
  Test / testOptions += Tests.Setup { () =>
    val port = (testServer2_13 / testServerPort).value
    PollingUtils.waitUntilServerAvailable(url(s"http://localhost:$port"))
  }
)

val circeVersion: String = "0.14.14"

val jsoniterVersion = "2.37.0"

val play29JsonVersion = "2.10.7"

val playJsonVersion = "3.0.5"

val catsEffect_3_version = "3.6.3"
val fs2_3_version = "3.12.0"

val catsEffect_2_version = "2.5.5"

val fs2_2_version = "2.5.12"

val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.2.10"
val akkaStreamVersion = "2.6.20"
val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaStreamVersion

val pekkoHttp = "org.apache.pekko" %% "pekko-http" % "1.2.0"
val pekkoStreamVersion = "1.1.5"
val pekkoStreams = "org.apache.pekko" %% "pekko-stream" % pekkoStreamVersion

val scalaTest = libraryDependencies ++= Seq("freespec", "funsuite", "flatspec", "wordspec", "shouldmatchers").map(m =>
  "org.scalatest" %%% s"scalatest-$m" % "3.2.19" % Test
)
val scalaTestPlusScalaCheck = libraryDependencies += "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test

val zio1Version = "1.0.18"
val zio2Version = "2.1.20"
val zio1InteropRsVersion = "1.3.12"
val zio2InteropRsVersion = "2.0.2"

val oxVersion = "0.6.1"
val sttpModelVersion = "1.7.15"
val sttpSharedVersion = "1.5.0"

val logback = "ch.qos.logback" % "logback-classic" % "1.5.14"

val jaegerClientVersion = "1.8.1"
val braveOpentracingVersion = "1.0.1"
val zipkinSenderOkHttpVersion = "3.5.1"
val resilience4jVersion = "2.3.0"
val http4s_ce2_version = "0.22.15"
val http4s_ce3_version = "0.23.30"
val osLibVersion = "0.11.4"
val tethysVersion = "0.29.5"
val openTelemetryVersion = "1.52.0"
val openTelemetrySemconvVersion = "1.34.0"
val otel4s = "0.13.1"
val slf4jVersion = "1.7.36"

val compileAndTest = "compile->compile;test->test"

lazy val loomProjects: Seq[String] = Seq(ox, examples).flatMap(_.projectRefs).flatMap(projectId)

def projectId(projectRef: ProjectReference): Option[String] =
  projectRef match {
    case ProjectRef(_, id) => Some(id)
    case LocalProject(id)  => Some(id)
    case _                 => None
  }

lazy val allAggregates: Seq[ProjectReference] = {
  val filteredByNative = if (sys.env.isDefinedAt("STTP_NATIVE")) {
    println("[info] STTP_NATIVE defined, including native in the aggregate projects")
    rawAllAggregates
  } else {
    println("[info] STTP_NATIVE *not* defined, *not* including native in the aggregate projects")
    rawAllAggregates.filterNot(_.toString.contains("Native"))
  }
  if (sys.env.isDefinedAt("ONLY_LOOM")) {
    println("[info] ONLY_LOOM defined, including only loom-based projects")
    filteredByNative.filter(p => projectId(p).forall(loomProjects.contains))
  } else if (sys.env.isDefinedAt("ALSO_LOOM")) {
    println("[info] ALSO_LOOM defined, including also loom-based projects")
    filteredByNative
  } else {
    println("[info] ONLY_LOOM *not* defined, *not* including loom-based-projects")
    filteredByNative.filterNot(p => projectId(p).forall(loomProjects.contains))
  }
}

lazy val rawAllAggregates =
  core.projectRefs ++
    jsonCommon.projectRefs ++
    upickle.projectRefs ++
    testCompilation.projectRefs ++
    catsCe2.projectRefs ++
    cats.projectRefs ++
    fs2Ce2.projectRefs ++
    fs2.projectRefs ++
    monix.projectRefs ++
    ox.projectRefs ++
    scalaz.projectRefs ++
    zio1.projectRefs ++
    zio.projectRefs ++
    akkaHttpBackend.projectRefs ++
    pekkoHttpBackend.projectRefs ++
    okhttpBackend.projectRefs ++
    okhttpMonixBackend.projectRefs ++
    http4sCe2Backend.projectRefs ++
    http4sBackend.projectRefs ++
    circe.projectRefs ++
    zio1Json.projectRefs ++
    zioJson.projectRefs ++
    json4s.projectRefs ++
    jsoniter.projectRefs ++
    sprayJson.projectRefs ++
    play29Json.projectRefs ++
    playJson.projectRefs ++
    tethysJson.projectRefs ++
    prometheusBackend.projectRefs ++
    openTelemetryBackend.projectRefs ++
    openTelemetryTracingZioBackend.projectRefs ++
    otel4sMetricsBackend.projectRefs ++
    otel4sTracingBackend.projectRefs ++
    finagleBackend.projectRefs ++
    armeriaBackend.projectRefs ++
    armeriaScalazBackend.projectRefs ++
    armeriaZio1Backend.projectRefs ++
    armeriaZioBackend.projectRefs ++
    armeriaMonixBackend.projectRefs ++
    armeriaCatsCe2Backend.projectRefs ++
    armeriaCatsBackend.projectRefs ++
    armeriaFs2Ce2Backend.projectRefs ++
    armeriaFs2Backend.projectRefs ++
    scribeBackend.projectRefs ++
    slf4jBackend.projectRefs ++
    cachingBackend.projectRefs ++
    examplesCe2.projectRefs ++
    examples.projectRefs ++
    docs.projectRefs ++
    testServer.projectRefs

def filterProject(p: String => Boolean) =
  ScopeFilter(inProjects(allAggregates.filter(pr => p(display(pr.project))): _*))

def filterByVersionAndPlatform(scalaVersionFilter: String, platformFilter: String) = filterProject { projectName =>
  val byPlatform =
    if (platformFilter == "JVM") !projectName.contains("JS") && !projectName.contains("Native")
    else projectName.contains(platformFilter)
  val byVersion = scalaVersionFilter match {
    case "3"    => projectName.contains("3")
    case "2.13" => !projectName.contains("2_12") && !projectName.contains("3")
    case "2.12" => projectName.contains("2_12")
  }

  byPlatform && byVersion
}

lazy val rootProject = (project in file("."))
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    name := "sttp",
    compileScoped := Def.inputTaskDyn {
      val args = spaceDelimited("<arg>").parsed
      Def.taskDyn((Compile / compile).all(filterByVersionAndPlatform(args.head, args(1))))
    }.evaluated,
    testScoped := Def.inputTaskDyn {
      val args = spaceDelimited("<arg>").parsed
      Def.taskDyn((Test / test).all(filterByVersionAndPlatform(args.head, args(1))))
    }.evaluated,
    ideSkipProject := false,
    scalaVersion := scala2_13,
    updateDocs := Def.taskDyn {
      val files1 = UpdateVersionInDocs(sLog.value, organization.value, version.value, List(file("README.md")))
      Def.task {
        (docs.jvm(scala3) / mdoc).toTask("").value
        // Generating the list only after mdoc is done (as it overrides what's in generated_doc)
        // For the root project the sourceDirectory points to src, so ../ will point to the root directory of the project
        GenerateListOfExamples(sLog.value, sourceDirectory.value.getParentFile)
        files1 ++ Seq(file("generated-docs/out"))
      }
    }.value
  )
  .aggregate(allAggregates: _*)

lazy val testServer = (projectMatrix in file("testing/server"))
  .settings(commonJvmSettings)
  .settings(
    name := "testing-server",
    libraryDependencies ++= Seq(
      akkaHttp,
      "ch.megard" %% "akka-http-cors" % "1.2.0",
      akkaStreams
    ),
    // the test server needs to be started before running any backend tests
    reStart / mainClass := Some("sttp.client4.testing.server.HttpServer"),
    reStart / reStartArgs := Seq(s"${(Test / testServerPort).value}"),
    reStart / fullClasspath := (Test / fullClasspath).value,
    testServerPort := 51823,
    startTestServer := reStart.toTask("").value
  )
  .jvmPlatform(scalaVersions = scala2)

lazy val testServer2_13 = testServer.jvm(scala2_13)

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.model" %%% "core" % sttpModelVersion,
      "com.softwaremill.sttp.shared" %%% "core" % sttpSharedVersion,
      "com.softwaremill.sttp.shared" %%% "ws" % sttpSharedVersion
    ),
    scalaTest,
    scalaTestPlusScalaCheck
  )
  .settings(testServerSettings)
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings ++ versioningSchemeSettings ++ /*enableMimaSettings ++*/ List(
      Test / publishArtifact := true, // allow implementations outside of this repo
      scalacOptions ++= Seq("-J--add-modules", "-Jjava.net.http")
    )
  )
  .jsPlatform(
    scalaVersions = scala2And3,
    settings =
      commonJsSettings ++ commonJsBackendSettings ++ browserChromeTestSettings ++ versioningSchemeSettings ++ List(
        Test / publishArtifact := true
      )
  )
  .nativePlatform(
    scalaVersions = scala2And3,
    settings = commonNativeSettings ++ versioningSchemeSettings ++ List(
      Test / publishArtifact := true
    )
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
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffect_2_version
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings
  )
  .jsPlatform(
    scalaVersions = scala2And3,
    settings = commonJsSettings ++ commonJsBackendSettings ++ browserChromeTestSettings ++ testServerSettings
  )

lazy val cats = (projectMatrix in file("effects/cats"))
  .settings(
    name := "cats",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect-kernel" % catsEffect_3_version,
      "org.typelevel" %%% "cats-effect-std" % catsEffect_3_version,
      "org.typelevel" %%% "cats-effect" % catsEffect_3_version % Test
    )
  )
  .settings(testServerSettings)
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings
  )
  .jsPlatform(
    scalaVersions = scala2And3,
    settings = commonJsSettings ++ commonJsBackendSettings ++ browserChromeTestSettings ++ testServerSettings
  )

lazy val fs2Ce2 = (projectMatrix in file("effects/fs2-ce2"))
  .settings(
    name := "fs2Ce2",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % fs2_2_version
    ),
    libraryDependencies += "com.softwaremill.sttp.shared" %% "fs2-ce2" % sttpSharedVersion
  )
  .settings(testServerSettings)
  .dependsOn(core % compileAndTest, catsCe2 % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings ++ Seq(
      libraryDependencies ++= Seq(
        "co.fs2" %%% "fs2-reactive-streams" % fs2_2_version,
        "co.fs2" %%% "fs2-io" % fs2_2_version
      )
    )
  )
  .jsPlatform(scalaVersions = scala2And3, settings = commonJsSettings)

lazy val fs2 = (projectMatrix in file("effects/fs2"))
  .settings(
    name := "fs2",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % fs2_3_version,
      "com.softwaremill.sttp.shared" %%% "fs2" % sttpSharedVersion
    )
  )
  .settings(testServerSettings)
  .dependsOn(core % compileAndTest, cats % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings ++ Seq(
      libraryDependencies ++= Seq(
        "co.fs2" %%% "fs2-reactive-streams" % fs2_3_version,
        "co.fs2" %%% "fs2-io" % fs2_3_version
      )
    )
  )
  .jsPlatform(scalaVersions = scala2And3, settings = commonJsSettings)

lazy val monix = (projectMatrix in file("effects/monix"))
  .settings(
    name := "monix",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq(
      "io.monix" %%% "monix" % "3.4.1",
      "com.softwaremill.sttp.shared" %%% "monix" % sttpSharedVersion
    )
  )
  .settings(testServerSettings)
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings ++ List(
      libraryDependencies ++= Seq("io.monix" %% "monix-nio" % "0.1.0")
    )
  )
  .jsPlatform(
    scalaVersions = scala2And3,
    settings = commonJsSettings ++ commonJsBackendSettings ++ browserChromeTestSettings ++ testServerSettings
  )

lazy val ox = (projectMatrix in file("effects/ox"))
  .settings(commonJvmSettings)
  .settings(
    name := "ox",
    libraryDependencies ++= Seq(
      "com.softwaremill.ox" %% "core" % oxVersion
    )
  )
  .settings(testServerSettings)
  .jvmPlatform(scalaVersions = List(scala3))
  .dependsOn(core % compileAndTest)

lazy val zio1 = (projectMatrix in file("effects/zio1"))
  .settings(
    name := "zio1",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-streams" % zio1Version,
      "dev.zio" %%% "zio" % zio1Version,
      "com.softwaremill.sttp.shared" %%% "zio1" % sttpSharedVersion
    )
  )
  .settings(testServerSettings)
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings ++ Seq(
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-interop-reactivestreams" % zio1InteropRsVersion,
        "dev.zio" %% "zio-nio" % "1.0.0-RC12"
      )
    )
  )
  .jsPlatform(
    scalaVersions = scala2And3,
    settings = commonJsSettings ++ commonJsBackendSettings ++ browserChromeTestSettings ++ testServerSettings
  )

lazy val zio = (projectMatrix in file("effects/zio"))
  .settings(
    name := "zio",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-streams" % zio2Version,
      "dev.zio" %%% "zio" % zio2Version,
      "com.softwaremill.sttp.shared" %%% "zio" % sttpSharedVersion
    )
  )
  .settings(testServerSettings)
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings ++ Seq(
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-interop-reactivestreams" % zio2InteropRsVersion
      )
    )
  )
  .jsPlatform(
    scalaVersions = scala2And3,
    settings = commonJsSettings ++ commonJsBackendSettings ++ browserChromeTestSettings ++ testServerSettings
  )

lazy val scalaz = (projectMatrix in file("effects/scalaz"))
  .settings(commonJvmSettings)
  .settings(
    name := "scalaz",
    Test / publishArtifact := true,
    libraryDependencies ++= Seq("org.scalaz" %% "scalaz-concurrent" % "7.2.36")
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
    scalaVersions = scala2
  )

//-- pekko
lazy val pekkoHttpBackend = (projectMatrix in file("pekko-http-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "pekko-http-backend",
    libraryDependencies ++= Seq(
      pekkoHttp,
      // provided as we don't want to create a transitive dependency on a specific streams version,
      // just as akka-http doesn't
      pekkoStreams % "provided",
      "com.softwaremill.sttp.shared" %% "pekko" % sttpSharedVersion
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = scala2And3
  )

//-- okhttp
lazy val okhttpBackend = (projectMatrix in file("okhttp-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "okhttp-backend",
    libraryDependencies ++= Seq(
      "com.squareup.okhttp3" % "okhttp" % "4.12.0"
    )
  )
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(core % compileAndTest)

def okhttpBackendProject(proj: String) =
  ProjectMatrix(s"okhttpBackend${proj.capitalize}", file(s"okhttp-backend/$proj"))
    .settings(commonJvmSettings)
    .settings(testServerSettings)
    .settings(name := s"okhttp-backend-$proj")
    .jvmPlatform(scalaVersions = scala2And3)
    .dependsOn(okhttpBackend % compileAndTest)

lazy val okhttpMonixBackend =
  okhttpBackendProject("monix")
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
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(catsCe2 % compileAndTest, core % compileAndTest, fs2Ce2 % compileAndTest)

lazy val http4sBackend = (projectMatrix in file("http4s-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "http4s-backend",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-client" % http4s_ce3_version,
      "org.http4s" %% "http4s-ember-client" % "0.23.30" % Optional,
      "org.http4s" %% "http4s-blaze-client" % "0.23.17" % Optional
    ),
    evictionErrorLevel := Level.Info
  )
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(cats % compileAndTest, core % compileAndTest, fs2 % compileAndTest)

//-- finagle backend
lazy val finagleBackend = (projectMatrix in file("finagle-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "finagle-backend",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-http" % "24.2.0"
    )
  )
  .jvmPlatform(scalaVersions = scala2)
  .dependsOn(core % compileAndTest)

lazy val armeriaBackend = (projectMatrix in file("armeria-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "armeria-backend",
    libraryDependencies += "com.linecorp.armeria" % "armeria" % "1.32.5"
  )
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(core % compileAndTest)

def armeriaBackendProject(proj: String, includeScala3: Boolean = true) =
  ProjectMatrix(s"armeriaBackend${proj.capitalize}", file(s"armeria-backend/$proj"))
    .settings(commonJvmSettings)
    .settings(testServerSettings)
    .settings(name := s"armeria-backend-$proj")
    .dependsOn(armeriaBackend % compileAndTest)
    .jvmPlatform(
      scalaVersions = scala2 ++ (if (includeScala3) List(scala3) else Nil)
    )

lazy val armeriaMonixBackend =
  armeriaBackendProject("monix")
    .dependsOn(monix % compileAndTest)

lazy val armeriaFs2Ce2Backend =
  armeriaBackendProject("fs2-ce2")
    .settings(
      libraryDependencies ++= Seq(
        "co.fs2" %% "fs2-reactive-streams" % fs2_2_version
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
  armeriaBackendProject("cats")
    .dependsOn(cats % compileAndTest)

lazy val armeriaScalazBackend =
  armeriaBackendProject("scalaz", includeScala3 = false)
    .dependsOn(scalaz % compileAndTest)

lazy val armeriaZio1Backend =
  armeriaBackendProject("zio1")
    .settings(
      libraryDependencies ++= Seq("dev.zio" %% "zio-interop-reactivestreams" % zio1InteropRsVersion)
    )
    .dependsOn(zio1 % compileAndTest)

lazy val armeriaZioBackend =
  armeriaBackendProject("zio")
    .settings(
      libraryDependencies ++= Seq("dev.zio" %% "zio-interop-reactivestreams" % zio2InteropRsVersion)
    )
    .dependsOn(zio % compileAndTest)

//----- json
lazy val jsonCommon = (projectMatrix in (file("json/common")))
  .settings(
    name := "json-common",
    scalaTest
  )
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = scala2And3, settings = commonJsSettings)
  .nativePlatform(scalaVersions = scala2And3, settings = commonNativeSettings)
  .dependsOn(core)

lazy val circe = (projectMatrix in file("json/circe"))
  .settings(
    name := "circe",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % circeVersion,
      "io.circe" %%% "circe-parser" % circeVersion,
      "io.circe" %%% "circe-generic" % circeVersion % Test
    ),
    scalaTest
  )
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = scala2And3, settings = commonJsSettings)
  .nativePlatform(scalaVersions = scala2And3, settings = commonNativeSettings)
  .dependsOn(core, jsonCommon % compileAndTest)

lazy val jsoniter = (projectMatrix in file("json/jsoniter"))
  .settings(
    name := "jsoniter",
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core" % jsoniterVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % jsoniterVersion % Test
    ),
    scalaTest
  )
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = scala2And3, settings = commonJsSettings)
  .nativePlatform(scalaVersions = scala2And3, settings = commonNativeSettings)
  .dependsOn(core, jsonCommon % compileAndTest)

lazy val zioJson = (projectMatrix in file("json/zio-json"))
  .settings(
    name := "zio-json",
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-json" % "0.7.44",
      "com.softwaremill.sttp.shared" %%% "zio" % sttpSharedVersion
    ),
    scalaTest
  )
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = scala2And3, settings = commonJsSettings)
  .dependsOn(core, jsonCommon % compileAndTest)

lazy val zio1Json = (projectMatrix in file("json/zio1-json"))
  .settings(
    name := "zio1-json",
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-json" % "0.2.0",
      "com.softwaremill.sttp.shared" %%% "zio1" % sttpSharedVersion
    ),
    scalaTest
  )
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = scala2And3, settings = commonJsSettings)
  .dependsOn(core, jsonCommon % compileAndTest)

lazy val tethysJson = (projectMatrix in file("json/tethys-json"))
  .settings(
    name := "tethys-json",
    libraryDependencies ++= Seq(
      "com.tethys-json" %% "tethys-core" % tethysVersion,
      "com.tethys-json" %% "tethys-jackson213" % tethysVersion % Test,
      "com.tethys-json" %% "tethys-derivation" % tethysVersion % Test
    ),
    scalaTest
  )
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings
  )
  .dependsOn(core, jsonCommon % compileAndTest)

lazy val upickle = (projectMatrix in file("json/upickle"))
  .settings(
    name := "upickle",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "upickle" % "4.2.1"
    ),
    scalaTest,
    // using macroRW causes a "match may not be exhaustive" error
    Test / scalacOptions --= Seq("-Wconf:cat=other-match-analysis:error")
  )
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = scala2And3, settings = commonJsSettings)
  .nativePlatform(scalaVersions = scala2And3, settings = commonNativeSettings)
  .dependsOn(core, jsonCommon % compileAndTest)

lazy val json4sVersion = "4.0.7"

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
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(core, jsonCommon % compileAndTest)

lazy val sprayJson = (projectMatrix in file("json/spray-json"))
  .settings(commonJvmSettings)
  .settings(
    name := "spray-json",
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-json" % "1.3.6"
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(core, jsonCommon % compileAndTest)

lazy val play29Json = (projectMatrix in file("json/play29-json"))
  .settings(
    name := "play29-json",
    Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "json" / "play-json" / "src" / "main" / "scala",
    Test / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "json" / "play-json" / "src" / "test" / "scala",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % play29JsonVersion
    ),
    scalaTest
  )
  .jvmPlatform(
    scalaVersions = scala2,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = scala2, settings = commonJsSettings)
  .dependsOn(core, jsonCommon % compileAndTest)

lazy val playJson = (projectMatrix in file("json/play-json"))
  .settings(
    name := "play-json",
    libraryDependencies ++= Seq(
      "org.playframework" %%% "play-json" % playJsonVersion
    ),
    scalaTest
  )
  .jvmPlatform(
    scalaVersions = scala2And3,
    settings = commonJvmSettings
  )
  .jsPlatform(scalaVersions = scala2And3, settings = commonJsSettings)
  .dependsOn(core, jsonCommon % compileAndTest)

lazy val prometheusBackend = (projectMatrix in file("observability/prometheus-backend"))
  .settings(commonJvmSettings)
  .settings(
    name := "prometheus-backend",
    libraryDependencies ++= Seq(
      "io.prometheus" % "prometheus-metrics-core" % "1.3.10"
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(core)

lazy val openTelemetryBackend = (projectMatrix in file("observability/opentelemetry-backend"))
  .settings(commonJvmSettings)
  .settings(
    name := "opentelemetry-backend",
    libraryDependencies ++= Seq(
      "io.opentelemetry.semconv" % "opentelemetry-semconv" % openTelemetrySemconvVersion,
      "io.opentelemetry" % "opentelemetry-api" % openTelemetryVersion,
      "io.opentelemetry" % "opentelemetry-sdk-testing" % openTelemetryVersion % Test
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(core)

lazy val openTelemetryTracingZioBackend = (projectMatrix in file("observability/opentelemetry-tracing-zio-backend"))
  .settings(commonJvmSettings)
  .settings(
    name := "opentelemetry-tracing-zio-backend",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-opentelemetry" % "3.1.7",
      "io.opentelemetry.semconv" % "opentelemetry-semconv" % openTelemetrySemconvVersion,
      "io.opentelemetry" % "opentelemetry-api" % openTelemetryVersion,
      "io.opentelemetry" % "opentelemetry-sdk-testing" % openTelemetryVersion % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(zio % compileAndTest)
  .dependsOn(core)

lazy val otel4sMetricsBackend = (projectMatrix in file("observability/otel4s-metrics-backend"))
  .settings(
    name := "opentelemetry-otel4s-metrics-backend",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "otel4s-core-metrics" % otel4s,
      "org.typelevel" %%% "otel4s-semconv" % otel4s,
      "org.typelevel" %%% "otel4s-semconv-metrics-experimental" % otel4s % Test,
      "org.typelevel" %%% "otel4s-sdk-metrics-testkit" % otel4s % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2_13And3, settings = commonJvmSettings)
  .jsPlatform(scalaVersions = scala2_13And3, settings = commonJsSettings)
  .dependsOn(cats % Test)
  .dependsOn(core % compileAndTest)

lazy val otel4sTracingBackend = (projectMatrix in file("observability/otel4s-tracing-backend"))
  .settings(
    name := "opentelemetry-otel4s-tracing-backend",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "otel4s-core-trace" % otel4s,
      "org.typelevel" %%% "otel4s-semconv" % otel4s,
      "org.typelevel" %%% "otel4s-sdk-trace-testkit" % otel4s % Test,
      "org.typelevel" %%% "cats-effect-testkit" % catsEffect_3_version % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2_13And3, settings = commonJvmSettings)
  .jsPlatform(scalaVersions = scala2_13And3, settings = commonJsSettings)
  .dependsOn(cats % Test)
  .dependsOn(core % compileAndTest)

lazy val scribeBackend = (projectMatrix in file("logging/scribe"))
  .settings(commonJvmSettings)
  .settings(
    name := "scribe-backend",
    libraryDependencies ++= Seq(
      "com.outr" %%% "scribe" % "3.15.2"
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = scala2And3, settings = commonJvmSettings)
  .jsPlatform(scalaVersions = scala2And3, settings = commonJsSettings)
  .nativePlatform(scalaVersions = scala2And3, settings = commonNativeSettings)
  .dependsOn(core)

lazy val slf4jBackend = (projectMatrix in file("logging/slf4j"))
  .settings(commonJvmSettings)
  .settings(
    name := "slf4j-backend",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % slf4jVersion
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(core)

lazy val cachingBackend = (projectMatrix in file("caching"))
  .settings(commonJvmSettings)
  .settings(
    name := "caching-backend",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % jsoniterVersion % Compile
    ),
    scalaTest
  )
  .jvmPlatform(scalaVersions = scala2And3)
  .dependsOn(core, jsoniter)

lazy val examplesCe2 = (projectMatrix in file("examples-ce2"))
  .settings(commonJvmSettings)
  .settings(
    name := "examples-ce2",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % circeVersion
    ),
    verifyExamplesCompileUsingScalaCli := VerifyExamplesCompileUsingScalaCli(sLog.value, sourceDirectory.value)
  )
  .jvmPlatform(scalaVersions = List(scala2_13))
  .dependsOn(circe, monix)

lazy val examples = (projectMatrix in file("examples"))
  .settings(commonJvmSettings)
  .settings(
    name := "examples",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % circeVersion,
      "org.json4s" %% "json4s-native" % json4sVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % jsoniterVersion,
      "io.github.resilience4j" % "resilience4j-circuitbreaker" % resilience4jVersion,
      "io.github.resilience4j" % "resilience4j-ratelimiter" % resilience4jVersion,
      "com.lihaoyi" %% "os-lib" % osLibVersion,
      "redis.clients" % "jedis" % "6.0.0",
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % openTelemetryVersion,
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % openTelemetryVersion,
      pekkoStreams,
      logback
    ),
    verifyExamplesCompileUsingScalaCli := VerifyExamplesCompileUsingScalaCli(sLog.value, sourceDirectory.value)
  )
  .jvmPlatform(scalaVersions = List(examplesScalaVersion))
  .dependsOn(
    core,
    fs2,
    zio,
    pekkoHttpBackend,
    json4s,
    circe,
    upickle,
    jsoniter,
    zioJson,
    scribeBackend,
    slf4jBackend,
    ox,
    cachingBackend,
    openTelemetryBackend
  )

//TODO this should be invoked by compilation process, see #https://github.com/scalameta/mdoc/issues/355
val compileDocs: TaskKey[Unit] = taskKey[Unit]("Compiles docs module throwing away its output")
compileDocs := {
  (docs.jvm(scala3) / mdoc).toTask(" --out target/sttp-docs").value
}

lazy val docs: ProjectMatrix = (projectMatrix in file("generated-docs")) // important: it must not be docs/
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    mdocIn := file("docs"),
    moduleName := "sttp-docs",
    mdocVariables := Map(
      "VERSION" -> version.value,
      "AKKA_STREAM_VERSION" -> akkaStreamVersion,
      "PEKKO_STREAM_VERSION" -> pekkoStreamVersion,
      "CIRCE_VERSION" -> circeVersion
    ),
    mdocOut := file("generated-docs/out"),
    mdocExtraArguments := Seq("--clean-target"),
    publishArtifact := false,
    name := "docs",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-native" % json4sVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterVersion,
      "commons-io" % "commons-io" % "2.20.0",
      "io.jaegertracing" % "jaeger-client" % jaegerClientVersion,
      "io.opentracing.brave" % "brave-opentracing" % braveOpentracingVersion,
      "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % zipkinSenderOkHttpVersion,
      "io.opentelemetry" % "opentelemetry-semconv" % "1.2.0-alpha",
      pekkoStreams
    ),
    evictionErrorLevel := Level.Info
  )
  .dependsOn(
    core % "compile->test",
    pekkoHttpBackend,
    json4s,
    circe,
    sprayJson,
    zioJson,
    jsoniter,
    upickle,
    armeriaZioBackend,
    // armeriaMonixBackend,
    armeriaFs2Backend,
    armeriaCatsBackend,
    okhttpBackend,
    // okhttpMonixBackend,
    http4sBackend,
    prometheusBackend,
    openTelemetryBackend,
    openTelemetryTracingZioBackend,
    otel4sMetricsBackend,
    otel4sTracingBackend,
    ox,
    slf4jBackend
  )
  .jvmPlatform(scalaVersions = List(documentationScalaVersion))
