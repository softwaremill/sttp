import com.softwaremill.Publish.Release.updateVersionInDocs
import sbt.Keys.publishArtifact
import sbt.Reference.display
import sbt.internal.ProjectMatrix
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

val scala2_11 = "2.11.12"
val scala2_12 = "2.12.12"
val scala2_13 = "2.13.3"
val scala3 = "0.27.0-RC1"

lazy val testServerPort = settingKey[Int]("Port to run the http test server on")
lazy val startTestServer = taskKey[Unit]("Start a http server used by tests")

excludeLintKeys in Global ++= Set(ideSkipProject, reStartArgs)

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp.client3",
  scmInfo := Some(ScmInfo(url("https://github.com/softwaremill/sttp"), "scm:git@github.com:softwaremill/sttp.git")),
  // needed on sbt 1.3, but (for some unknown reason) only on 2.11.x
  closeClassLoaders := !scalaVersion.value.startsWith("2.11."),
  // cross-release doesn't work when subprojects have different cross versions
  // work-around from https://github.com/sbt/sbt-release/issues/214,
  releaseCrossBuild := false,
  releaseProcess := Seq(
    checkSnapshotDependencies,
    inquireVersions,
    // publishing locally so that the pgp password prompt is displayed early
    // in the process
    releaseStepCommandAndRemaining("publishLocalSigned"),
    releaseStepCommandAndRemaining("clean"),
    releaseStepCommandAndRemaining("test"),
    setReleaseVersion,
    releaseStepInputTask(docs.jvm(scala2_13) / mdoc),
    Release.stageChanges("generated-docs/out"),
    updateVersionInDocs(organization.value),
    commitReleaseVersion,
    tagRelease,
    releaseStepCommandAndRemaining("publishSigned"),
    releaseStepCommand("sonatypeBundleRelease"),
    pushChanges
  ),
  // doc generation is broken in dotty
  sources in (Compile, doc) := {
    val scalaV = scalaVersion.value
    val current = (sources in (Compile, doc)).value
    if (scalaV == scala3) Seq() else current
  },
  sources in (Test, doc) := {
    val scalaV = scalaVersion.value
    val current = (sources in (Test, doc)).value
    if (scalaV == scala3) Seq() else current
  }
)

val commonJvmSettings = commonSettings ++ Seq(
  scalacOptions ++= Seq("-target:jvm-1.8"),
  scalacOptions := {
    val current = scalacOptions.value
    // https://github.com/lampepfl/dotty/pull/7775
    if (isDotty.value) current ++ List("-language:implicitConversions", "-Ykind-projector") else current
  }
)

val commonJsSettings = commonSettings ++ Seq(
  // slow down for CI
  parallelExecution in Test := false, // TODO
  // https://github.com/scalaz/scalaz/pull/1734#issuecomment-385627061
  scalaJSLinkerConfig ~= {
    _.withBatchMode(System.getenv("CONTINUOUS_INTEGRATION") == "true")
  },
  scalacOptions in Compile ++= {
    if (isSnapshot.value) Seq.empty
    else
      Seq {
        val dir = project.base.toURI.toString.replaceFirst("[^/]+/?$", "")
        val url = "https://raw.githubusercontent.com/softwaremill/sttp"
        s"-P:scalajs:mapSourceURI:$dir->$url/v${version.value}/"
      }
  }
)

val commonJsBackendSettings = JSDependenciesPlugin.projectSettings ++ List(
  jsDependencies ++= Seq(
    "org.webjars.npm" % "spark-md5" % "3.0.0" % Test / "spark-md5.js" minified "spark-md5.min.js"
  )
)

val commonNativeSettings = commonSettings ++ Seq(
  nativeLinkStubs := true
)

lazy val downloadChromeDriver = taskKey[Unit]("Download chrome driver corresponding to installed google-chrome version")
Global / downloadChromeDriver := {
  if (java.nio.file.Files.notExists(new File("target", "chromedriver").toPath)) {
    println("ChromeDriver binary file not found. Detecting google-chrome version...")
    import sys.process._
    val osName = sys.props("os.name")
    val isMac = osName.toLowerCase.contains("mac")
    val isWin = osName.toLowerCase.contains("win")
    val chromeVersionExecutable =
      if (isMac)
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
      else "google-chrome"
    val chromeVersion = Seq(chromeVersionExecutable, "--version").!!.split(' ')(2)
    println(s"Detected google-chrome version: $chromeVersion")
    val withoutLastPart = chromeVersion.split('.').dropRight(1).mkString(".")
    println(s"Selected release: $withoutLastPart")
    val latestVersion =
      IO.readLinesURL(new URL(s"https://chromedriver.storage.googleapis.com/LATEST_RELEASE_$withoutLastPart")).mkString
    val platformDependentName = if (isMac) {
      "chromedriver_mac64.zip"
    } else if (isWin) {
      "chromedriver_win32.zip"
    } else {
      "chromedriver_linux64.zip"
    }
    println(s"Downloading chrome driver version $latestVersion for $osName")
    IO.unzipURL(
      new URL(s"https://chromedriver.storage.googleapis.com/$latestVersion/$platformDependentName"),
      new File("target")
    )
    IO.chmod("rwxrwxr-x", new File("target", "chromedriver"))
  } else {
    println("Detected chromedriver binary file, skipping downloading.")
  }
}

// run JS tests inside Chrome, due to jsdom not supporting fetch
val browserTestSettings = Seq(
  jsEnv in Test := {
    val debugging = false // set to true to help debugging
    System.setProperty("webdriver.chrome.driver", "target/chromedriver")
    new org.scalajs.jsenv.selenium.SeleniumJSEnv(
      {
        val options = new org.openqa.selenium.chrome.ChromeOptions()
        val args = Seq(
          "auto-open-devtools-for-tabs", // devtools needs to be open to capture network requests
          "no-sandbox",
          "allow-file-access-from-files" // change the origin header from 'null' to 'file'
        ) ++ (if (debugging) Seq.empty else Seq("headless"))
        options.addArguments(args: _*)
        val capabilities = org.openqa.selenium.remote.DesiredCapabilities.chrome()
        capabilities.setCapability(org.openqa.selenium.chrome.ChromeOptions.CAPABILITY, options)
        capabilities
      },
      org.scalajs.jsenv.selenium.SeleniumJSEnv.Config().withKeepAlive(debugging)
    )
  },
  test in Test := (test in Test)
    .dependsOn(downloadChromeDriver in Global)
    .value
)

// start a test server before running tests of a backend; this is required both for JS tests run inside a
// nodejs/browser environment, as well as for JVM tests where akka-http isn't available (e.g. dotty). To simplify
// things, we always start the test server.
val testServerSettings = Seq(
  test in Test := (test in Test)
    .dependsOn(startTestServer in testServer2_13)
    .value,
  testOnly in Test := (testOnly in Test)
    .dependsOn(startTestServer in testServer2_13)
    .evaluated,
  testOptions in Test += Tests.Setup(() => {
    val port = (testServerPort in testServer2_13).value
    PollingUtils.waitUntilServerAvailable(new URL(s"http://localhost:$port"))
  })
)

def intellijImportOnly213 = ideSkipProject := (scalaVersion.value != scala2_13)
def intellijSkipImport = ideSkipProject := true

val circeVersion: Option[(Long, Long)] => String = {
  case Some((2, 11)) => "0.11.2"
  case _             => "0.13.0"
}
val playJsonVersion: Option[(Long, Long)] => String = {
  case Some((2, 11)) => "2.7.4"
  case _             => "2.9.1"
}
val catsEffectVersion: Option[(Long, Long)] => String = {
  case Some((2, 11)) => "2.0.0"
  case _             => "2.1.4"
}
val fs2Version: Option[(Long, Long)] => String = {
  case Some((2, 11)) => "2.1.0"
  case _             => "2.4.4"
}

val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.2.1"
val akkaStreamVersion = "2.6.10"
val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaStreamVersion

val scalaTestVersion = "3.2.2"
val scalaNativeTestInterfaceVersion = "0.4.0-M2"
val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion

val zioVersion = "1.0.3"
val zioInteropRsVersion = "1.0.3.5"

val sttpModelVersion = "1.2.0-RC5"
val sttpSharedVersion = "1.0.0-RC7"

val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

val jeagerClientVersion = "1.4.0"
val braveOpentracingVersion = "0.37.2"
val zipkinSenderOkHttpVersion = "2.15.4"
val resilience4jVersion = "1.6.1"

val compileAndTest = "compile->compile;test->test"

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

lazy val coreProjectAggregates: Seq[ProjectReference] = if (sys.env.isDefinedAt("STTP_NATIVE")) {
  println("[info] STTP_NATIVE defined, including sttp-native in the aggregate projects")
  core.projectRefs
} else {
  println("[info] STTP_NATIVE *not* defined, *not* including sttp-native in the aggregate projects")
  List(
    core.jvm(scala2_11),
    core.jvm(scala2_12),
    core.jvm(scala2_13),
    core.jvm(scala3),
    core.js(scala2_11),
    core.js(scala2_12),
    core.js(scala2_13)
  )
}

lazy val allAggregates = coreProjectAggregates ++
  testCompilation.projectRefs ++
  cats.projectRefs ++
  fs2.projectRefs ++
  monix.projectRefs ++
  scalaz.projectRefs ++
  zio.projectRefs ++
  // might fail due to // https://github.com/akka/akka-http/issues/1930
  akkaHttpBackend.projectRefs ++
  asyncHttpClientBackend.projectRefs ++
  asyncHttpClientFutureBackend.projectRefs ++
  asyncHttpClientScalazBackend.projectRefs ++
  asyncHttpClientZioBackend.projectRefs ++
  asyncHttpClientMonixBackend.projectRefs ++
  asyncHttpClientCatsBackend.projectRefs ++
  asyncHttpClientFs2Backend.projectRefs ++
  okhttpBackend.projectRefs ++
  okhttpMonixBackend.projectRefs ++
  http4sBackend.projectRefs ++
  jsonCommon.projectRefs ++
  circe.projectRefs ++
  json4s.projectRefs ++
  sprayJson.projectRefs ++
  playJson.projectRefs ++
  openTracingBackend.projectRefs ++
  prometheusBackend.projectRefs ++
  zioTelemetryOpenTracingBackend.projectRefs ++
  httpClientBackend.projectRefs ++
  httpClientMonixBackend.projectRefs ++
  httpClientFs2Backend.projectRefs ++
  httpClientZioBackend.projectRefs ++
  finagleBackend.projectRefs ++
  scribeBackend.projectRefs ++
  slf4jBackend.projectRefs ++
  examples.projectRefs ++
  docs.projectRefs

// For Travis tests, defining scripts that run JVM/JS/Native tests separately
val testJVM = taskKey[Unit]("Test JVM projects")
val testJS = taskKey[Unit]("Test JS projects")
val testNative = taskKey[Unit]("Test native projects")

def filterProject(p: String => Boolean) =
  ScopeFilter(inProjects(allAggregates.filter(pr => p(display(pr.project))): _*))

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    skip in publish := true,
    name := "sttp",
    testJVM := (test in Test).all(filterProject(p => !p.contains("JS") && !p.contains("Native"))).value,
    testJS := (test in Test).all(filterProject(_.contains("JS"))).value,
    testNative := (test in Test).all(filterProject(_.contains("Native"))).value
  )
  .aggregate(allAggregates: _*)

lazy val testServer = (projectMatrix in file("testing/server"))
  .settings(commonJvmSettings)
  .settings(
    name := "testing-server",
    skip in publish := true,
    libraryDependencies ++= Seq(
      akkaHttp,
      "ch.megard" %% "akka-http-cors" % "0.4.2",
      akkaStreams
    ),
    // the test server needs to be started before running any backend tests
    mainClass in reStart := Some("sttp.client3.testing.server.HttpServer"),
    reStartArgs in reStart := Seq(s"${(testServerPort in Test).value}"),
    fullClasspath in reStart := (fullClasspath in Test).value,
    testServerPort := 51823,
    startTestServer := reStart.toTask("").value
  )
  .settings(intellijImportOnly213)
  .jvmPlatform(scalaVersions = List(scala2_13))

lazy val testServer2_13 = testServer.jvm(scala2_13)

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.model" %%% "core" % sttpModelVersion,
      "com.softwaremill.sttp.shared" %%% "core" % sttpSharedVersion,
      "com.softwaremill.sttp.shared" %%% "ws" % sttpSharedVersion
    )
  )
  .settings(testServerSettings)
  .jvmPlatform(
    scalaVersions = List(scala2_11, scala2_12, scala2_13, scala3),
    settings = {
      commonJvmSettings ++ intellijImportOnly213 ++ List(
        libraryDependencies ++= Seq(
          scalaTest % Test
        ),
        publishArtifact in Test := true // allow implementations outside of this repo
      )
    }
  )
  .jsPlatform(
    scalaVersions = List(scala2_11, scala2_12, scala2_13),
    settings = {
      commonJsSettings ++ commonJsBackendSettings ++ browserTestSettings ++ intellijSkipImport ++ List(
        libraryDependencies ++= Seq(
          "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
        ),
        publishArtifact in Test := true
      )
    }
  )
  .nativePlatform(
    scalaVersions = List(scala2_11),
    settings = {
      commonNativeSettings ++ intellijSkipImport ++ List(
        libraryDependencies ++= Seq(
          "org.scala-native" %%% "test-interface" % scalaNativeTestInterfaceVersion % Test,
          "org.scalatest" %%% "scalatest-shouldmatchers" % scalaTestVersion % Test,
          "org.scalatest" %%% "scalatest-flatspec" % scalaTestVersion % Test,
          "org.scalatest" %%% "scalatest-freespec" % scalaTestVersion % Test,
          "org.scalatest" %%% "scalatest-funsuite" % scalaTestVersion % Test
        ),
        publishArtifact in Test := true
      )
    }
  )

lazy val testCompilation = (projectMatrix in file("testing/compile"))
  .settings(commonJvmSettings)
  .settings(
    name := "testing-compile",
    skip in publish := true,
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % Test,
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_13))
  .dependsOn(core % Test)

//----- implementations
lazy val cats = (projectMatrix in file("implementations/cats"))
  .settings(
    name := "cats",
    publishArtifact in Test := true,
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "org.typelevel" %%% "cats-effect" % catsEffectVersion(_)
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = List(scala2_11, scala2_12, scala2_13),
    settings = commonJvmSettings ++ intellijImportOnly213
  )
  .jsPlatform(scalaVersions = List(scala2_12, scala2_13), settings = commonJsSettings ++ intellijSkipImport)

lazy val fs2 = (projectMatrix in file("implementations/fs2"))
  .settings(
    name := "fs2",
    publishArtifact in Test := true,
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "co.fs2" %%% "fs2-core" % fs2Version(_)
    ),
    libraryDependencies += "com.softwaremill.sttp.shared" %% "fs2" % sttpSharedVersion
  )
  .dependsOn(core % compileAndTest, cats % compileAndTest)
  .jvmPlatform(
    scalaVersions = List(scala2_11, scala2_12, scala2_13),
    settings = commonJvmSettings ++ intellijImportOnly213
  )
  .jsPlatform(scalaVersions = List(scala2_12, scala2_13), settings = commonJsSettings ++ intellijSkipImport)

lazy val monix = (projectMatrix in file("implementations/monix"))
  .settings(
    name := "monix",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq(
      "io.monix" %%% "monix" % "3.2.2",
      "com.softwaremill.sttp.shared" %%% "monix" % sttpSharedVersion
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = List(scala2_11, scala2_12, scala2_13),
    settings = commonJvmSettings ++ intellijImportOnly213 ++ List(
      libraryDependencies ++= Seq("io.monix" %% "monix-nio" % "0.0.9")
    )
  )
  .jsPlatform(
    scalaVersions = List(scala2_12, scala2_13),
    settings =
      commonJsSettings ++ intellijSkipImport ++ commonJsBackendSettings ++ browserTestSettings ++ testServerSettings
  )

lazy val zio = (projectMatrix in file("implementations/zio"))
  .settings(commonJvmSettings)
  .settings(
    name := "zio",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio" % zioVersion,
      "com.softwaremill.sttp.shared" %% "zio" % sttpSharedVersion
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = List(scala2_11, scala2_12, scala2_13, scala3),
    settings = intellijImportOnly213
  )

lazy val scalaz = (projectMatrix in file("implementations/scalaz"))
  .settings(commonJvmSettings)
  .settings(
    name := "scalaz",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq("org.scalaz" %% "scalaz-concurrent" % "7.2.30")
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = List(scala2_11, scala2_12, scala2_13),
    settings = intellijImportOnly213
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
    scalaVersions = List(scala2_12, scala2_13),
    settings = intellijImportOnly213
  )

//-- async http client
lazy val asyncHttpClientBackend = (projectMatrix in file("async-http-client-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "async-http-client-backend",
    libraryDependencies ++= Seq(
      "org.asynchttpclient" % "async-http-client" % "2.12.1"
    )
  )
  .dependsOn(core % compileAndTest)
  .jvmPlatform(
    scalaVersions = List(scala2_11, scala2_12, scala2_13, scala3),
    settings = intellijImportOnly213
  )

def asyncHttpClientBackendProject(proj: String, includeDotty: Boolean = false) = {
  ProjectMatrix(s"asyncHttpClientBackend${proj.capitalize}", file(s"async-http-client-backend/$proj"))
    .settings(commonJvmSettings)
    .settings(testServerSettings)
    .settings(name := s"async-http-client-backend-$proj")
    .dependsOn(asyncHttpClientBackend % compileAndTest)
    .jvmPlatform(
      scalaVersions = List(scala2_11, scala2_12, scala2_13) ++ (if (includeDotty) List(scala3) else Nil),
      settings = intellijImportOnly213
    )
}

lazy val asyncHttpClientFutureBackend =
  asyncHttpClientBackendProject("future", includeDotty = true)
    .dependsOn(core % compileAndTest)

lazy val asyncHttpClientScalazBackend =
  asyncHttpClientBackendProject("scalaz")
    .dependsOn(scalaz % compileAndTest)

lazy val asyncHttpClientZioBackend =
  asyncHttpClientBackendProject("zio", includeDotty = false)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-interop-reactivestreams" % zioInteropRsVersion
      )
    )
    .dependsOn(zio % compileAndTest)

lazy val asyncHttpClientMonixBackend =
  asyncHttpClientBackendProject("monix")
    .dependsOn(monix % compileAndTest)

lazy val asyncHttpClientCatsBackend =
  asyncHttpClientBackendProject("cats")
    .dependsOn(cats % compileAndTest)

lazy val asyncHttpClientFs2Backend =
  asyncHttpClientBackendProject("fs2")
    .settings(
      libraryDependencies ++= dependenciesFor(scalaVersion.value)(
        "co.fs2" %% "fs2-reactive-streams" % fs2Version(_),
        "co.fs2" %% "fs2-io" % fs2Version(_)
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
      "com.squareup.okhttp3" % "okhttp" % "4.9.0"
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_11, scala2_12, scala2_13), settings = intellijImportOnly213)
  .dependsOn(core % compileAndTest)

def okhttpBackendProject(proj: String) = {
  ProjectMatrix(s"okhttpBackend${proj.capitalize}", file(s"okhttp-backend/$proj"))
    .settings(commonJvmSettings)
    .settings(testServerSettings)
    .settings(name := s"okhttp-backend-$proj")
    .jvmPlatform(scalaVersions = List(scala2_11, scala2_12, scala2_13), settings = intellijImportOnly213)
    .dependsOn(okhttpBackend)
}

lazy val okhttpMonixBackend =
  okhttpBackendProject("monix")
    .dependsOn(monix % compileAndTest)

//-- http4s
lazy val http4sBackend = (projectMatrix in file("http4s-backend"))
  .settings(commonJvmSettings)
  .settings(testServerSettings)
  .settings(
    name := "http4s-backend",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-client" % "0.21.8"
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13), settings = intellijImportOnly213)
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
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13, scala3), settings = intellijImportOnly213)
  .dependsOn(core % compileAndTest)

def httpClientBackendProject(proj: String, includeDotty: Boolean = false) = {
  ProjectMatrix(s"httpClientBackend${proj.capitalize}", file(s"httpclient-backend/$proj"))
    .settings(commonJvmSettings)
    .settings(testServerSettings)
    .settings(name := s"httpclient-backend-$proj")
    .jvmPlatform(
      scalaVersions = List(scala2_12, scala2_13) ++ (if (includeDotty) List(scala3) else Nil),
      settings = intellijImportOnly213
    )
    .dependsOn(httpClientBackend % compileAndTest)
}

lazy val httpClientMonixBackend =
  httpClientBackendProject("monix")
    .dependsOn(monix % compileAndTest)

lazy val httpClientFs2Backend =
  httpClientBackendProject("fs2")
    .settings(
      libraryDependencies ++= dependenciesFor(scalaVersion.value)(
        "co.fs2" %% "fs2-reactive-streams" % fs2Version(_),
        "co.fs2" %% "fs2-io" % fs2Version(_)
      )
    )
    .dependsOn(fs2 % compileAndTest)

lazy val httpClientZioBackend =
  httpClientBackendProject("zio", includeDotty = false)
    .settings(
      libraryDependencies ++=
        Seq(
          "dev.zio" %% "zio-interop-reactivestreams" % zioInteropRsVersion,
          "dev.zio" %% "zio-nio" % "1.0.0-RC10"
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
      "com.twitter" %% "finagle-http" % "20.10.0"
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_11, scala2_12, scala2_13), settings = intellijImportOnly213)
  .dependsOn(core % compileAndTest)

//----- json
lazy val jsonCommon = (projectMatrix in (file("json/common")))
  .settings(
    name := "json-common"
  )
  .jvmPlatform(
    scalaVersions = List(scala2_11, scala2_12, scala2_13),
    settings = commonJvmSettings ++ intellijImportOnly213
  )
  .jsPlatform(scalaVersions = List(scala2_11, scala2_12, scala2_13), settings = commonJsSettings ++ intellijSkipImport)
  .dependsOn(core)

lazy val circe = (projectMatrix in file("json/circe"))
  .settings(
    name := "circe",
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "io.circe" %%% "circe-core" % circeVersion(_),
      "io.circe" %%% "circe-parser" % circeVersion(_),
      "io.circe" %%% "circe-generic" % circeVersion(_) % Test,
      _ => "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
    )
  )
  .jvmPlatform(
    scalaVersions = List(scala2_11, scala2_12, scala2_13),
    settings = commonJvmSettings ++ intellijImportOnly213
  )
  .jsPlatform(scalaVersions = List(scala2_12, scala2_13), settings = commonJsSettings ++ intellijSkipImport)
  .dependsOn(core, jsonCommon)

lazy val json4sVersion = "3.6.10"

lazy val json4s = (projectMatrix in file("json/json4s"))
  .settings(commonJvmSettings)
  .settings(
    name := "json4s",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-core" % json4sVersion,
      "org.json4s" %% "json4s-native" % json4sVersion % Test,
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_11, scala2_12, scala2_13), settings = intellijImportOnly213)
  .dependsOn(core, jsonCommon)

lazy val sprayJson = (projectMatrix in file("json/spray-json"))
  .settings(commonJvmSettings)
  .settings(
    name := "spray-json",
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-json" % "1.3.5",
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_11, scala2_12, scala2_13), settings = intellijImportOnly213)
  .dependsOn(core, jsonCommon)

lazy val playJson = (projectMatrix in file("json/play-json"))
  .settings(
    name := "play-json",
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "com.typesafe.play" %%% "play-json" % playJsonVersion(_),
      _ => "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
    )
  )
  .jvmPlatform(
    scalaVersions = List(scala2_11, scala2_12, scala2_13),
    settings = commonJvmSettings ++ intellijImportOnly213
  )
  .jsPlatform(scalaVersions = List(scala2_12, scala2_13), settings = commonJsSettings ++ intellijSkipImport)
  .dependsOn(core, jsonCommon)

lazy val openTracingBackend = (projectMatrix in file("metrics/open-tracing-backend"))
  .settings(commonJvmSettings)
  .settings(
    name := "opentracing-backend",
    libraryDependencies ++= Seq(
      "io.opentracing" % "opentracing-api" % "0.33.0",
      "io.opentracing" % "opentracing-mock" % "0.33.0" % Test,
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_11, scala2_12, scala2_13), settings = intellijImportOnly213)
  .dependsOn(core)

lazy val prometheusBackend = (projectMatrix in file("metrics/prometheus-backend"))
  .settings(commonJvmSettings)
  .settings(
    name := "prometheus-backend",
    libraryDependencies ++= Seq(
      "io.prometheus" % "simpleclient" % "0.9.0",
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_11, scala2_12, scala2_13), settings = intellijImportOnly213)
  .dependsOn(core)

lazy val zioTelemetryOpenTracingBackend = (projectMatrix in file("metrics/zio-telemetry-open-tracing-backend"))
  .settings(commonJvmSettings)
  .settings(
    name := "zio-telemetry-opentracing-backend",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-opentracing" % "0.7.0",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.2.0"
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13), settings = intellijImportOnly213)
  .dependsOn(zio % compileAndTest)
  .dependsOn(core)

lazy val scribeBackend = (projectMatrix in file("logging/scribe"))
  .settings(commonJvmSettings)
  .settings(
    name := "scribe-backend",
    libraryDependencies ++= Seq(
      "com.outr" %%% "scribe" % "3.0.0",
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13), settings = intellijImportOnly213)
  .dependsOn(core)

lazy val slf4jBackend = (projectMatrix in file("logging/slf4j"))
  .settings(commonJvmSettings)
  .settings(
    name := "slf4j-backend",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "1.7.30",
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_11, scala2_12, scala2_13), settings = intellijImportOnly213)
  .dependsOn(core)

lazy val examples = (projectMatrix in file("examples"))
  .settings(commonJvmSettings)
  .settings(
    name := "examples",
    skip in publish := true,
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "io.circe" %% "circe-generic" % circeVersion(_),
      _ => "org.json4s" %% "json4s-native" % json4sVersion,
      _ => akkaStreams,
      _ => logback
    )
  )
  .jvmPlatform(scalaVersions = List(scala2_12, scala2_13), settings = intellijImportOnly213)
  .dependsOn(
    core,
    asyncHttpClientMonixBackend,
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
      "io.circe" %% "circe-generic" % "0.13.0",
      "commons-io" % "commons-io" % "2.8.0",
      "io.github.resilience4j" % "resilience4j-circuitbreaker" % resilience4jVersion,
      "io.github.resilience4j" % "resilience4j-ratelimiter" % resilience4jVersion,
      "io.jaegertracing" % "jaeger-client" % jeagerClientVersion,
      "io.opentracing.brave" % "brave-opentracing" % braveOpentracingVersion,
      "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % zipkinSenderOkHttpVersion,
      akkaStreams
    )
  )
  .dependsOn(
    core % "compile->test",
    akkaHttpBackend,
    json4s,
    circe,
    sprayJson,
    asyncHttpClientZioBackend,
    asyncHttpClientMonixBackend,
    asyncHttpClientFs2Backend,
    asyncHttpClientCatsBackend,
    asyncHttpClientFutureBackend,
    asyncHttpClientScalazBackend,
    okhttpBackend,
    okhttpMonixBackend,
    httpClientBackend,
    httpClientFs2Backend,
    http4sBackend,
    httpClientMonixBackend,
    httpClientZioBackend,
    openTracingBackend,
    prometheusBackend,
    slf4jBackend,
    zioTelemetryOpenTracingBackend
  )
  .jvmPlatform(scalaVersions = List(scala2_13))
