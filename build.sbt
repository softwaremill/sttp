// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.{CrossType, crossProject}
import Versions._
import Dependencies._

lazy val testServerPort = settingKey[Int]("Port to run the http test server on (used by JS tests)")
lazy val startTestServer = taskKey[Unit]("Start a http server used by tests (used by JS tests)")

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp",
  scalaVersion := scalaCompilerVersion,
  crossScalaVersions := Seq(scalaCompilerVersion, "2.11.12")
)

val commonJSSettings = Seq(
  // slow down for CI
  parallelExecution in Test := false,
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
) ++ browserTestSettings

// run JS tests inside Chrome, due to jsdom not supporting fetch
lazy val browserTestSettings = Seq(
  jsEnv in Test := {
    val debugging = false // set to true to help debugging

    new org.scalajs.jsenv.selenium.SeleniumJSEnv(
      {
        val options = new org.openqa.selenium.chrome.ChromeOptions()
        val args = Seq(
          "auto-open-devtools-for-tabs", // devtools needs to be open to capture network requests
          "allow-file-access-from-files" // change the origin header from 'null' to 'file'
        ) ++ (if (debugging) Seq.empty else Seq("headless"))
        options.addArguments(args: _*)
        val capabilities = org.openqa.selenium.remote.DesiredCapabilities.chrome()
        capabilities.setCapability(org.openqa.selenium.chrome.ChromeOptions.CAPABILITY, options)
        capabilities
      },
      org.scalajs.jsenv.selenium.SeleniumJSEnv.Config().withKeepAlive(debugging)
    )
  }
)

// start a test server before running tests; this is required as JS tests run inside a nodejs/browser environment
def testServerSettings(config: Configuration) = Seq(
  test in config := (test in config)
    .dependsOn(
      startTestServer in Test in Project("core", file("core"))
    )
    .value,
  testOnly in config := (testOnly in config)
    .dependsOn(
      startTestServer in Test in Project("core", file("core"))
    )
    .evaluated,
  testOptions in config += Tests.Setup(() => {
    val port = (testServerPort in Test in Project("core", file("core"))).value
    PollingUtils.waitUntilServerAvailable(new URL(s"http://localhost:$port"))
  })
)

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(skip in publish := true, name := "sttp")
  .aggregate(
    rootJVM,
    rootJS
  )

lazy val rootJVM = project
  .in(file(".jvm"))
  .settings(commonSettings: _*)
  .settings(skip in publish := true, name := "sttpJVM")
  .aggregate(
    coreJVM,
    catsJVM,
    monixJVM,
    scalaz,
    // might fail due to // https://github.com/akka/akka-http/issues/1930
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
    prometheusBackend
  )

lazy val rootJS = project
  .in(file(".js"))
  .settings(commonSettings: _*)
  .settings(skip in publish := true, name := "sttpJS")
  .aggregate(coreJS, catsJS, monixJS, circeJS)

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
    publishArtifact in Test := true // allow implementations outside of this repo
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % scalaJsDomVersion
    ),
    jsDependencies ++= Seq(
      sparkMd5Test / "spark-md5.js" minified "spark-md5.min.js"
    )
  )
  .jsSettings(browserTestSettings)
  .jsSettings(testServerSettings(Test))
  .jvmSettings(
    libraryDependencies ++= Seq(
      akkaHttpTest,
      akkaHttpCorsTest,
      akkaStreamsTest,
      scalaCompilerTest
    ),
    // the test server needs to be started before running any JS tests
    // `reStart` cannJSAsyncExecutionContextot be scoped so it can't be only added to Test
    mainClass in reStart := Some("com.softwaremill.sttp.testing.HttpServer"),
    reStartArgs in reStart := Seq(s"${(testServerPort in Test).value}"),
    fullClasspath in reStart := (fullClasspath in Test).value,
    testServerPort in Test := 51823,
    startTestServer in Test := reStart.toTask("").value
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
    publishArtifact in Test := true,
    libraryDependencies ++= Seq("org.typelevel" %%% "cats-effect" % catzVersion)
  )
lazy val catsJS = cats.js.dependsOn(coreJS % "compile->compile;test->test")
lazy val catsJVM = cats.jvm.dependsOn(coreJVM % "compile->compile;test->test")

lazy val monix = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("implementations/monix"))
  .settings(commonSettings: _*)
  .jsSettings(commonJSSettings: _*)
  .jsSettings(testServerSettings(Test))
  .settings(
    name := "monix",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq("io.monix" %%% "monix" % monixVersion)
  )
lazy val monixJS = monix.js.dependsOn(coreJS % "compile->compile;test->test")
lazy val monixJVM = monix.jvm.dependsOn(coreJVM % "compile->compile;test->test")

lazy val scalaz: Project = (project in file("implementations/scalaz"))
  .settings(commonSettings: _*)
  .settings(
    name := "scalaz",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq(scalazConcurrent)
  )
  .dependsOn(coreJVM % "compile->compile;test->test")

//----- backends
//-- akka
lazy val akkaHttpBackend: Project = (project in file("akka-http-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "akka-http-backend",
    libraryDependencies ++= Seq(
      akkaHttp,
      // provided as we don't want to create a transitive dependency on a specific streams version,
      // just as akka-http doesn't
      akkaStreamProvided
    )
  )
  .dependsOn(coreJVM % "compile->compile;test->test")

//-- async http client
lazy val asyncHttpClientBackend: Project =
  (project in file("async-http-client-backend"))
    .settings(commonSettings: _*)
    .settings(
      name := "async-http-client-backend",
      libraryDependencies ++= Seq(
        asyncHttpClient
      )
    )
    .dependsOn(coreJVM % "compile->compile;test->test")

def asyncHttpClientBackendProject(proj: String): Project =
  Project(s"asyncHttpClientBackend${proj.capitalize}", file(s"async-http-client-backend/$proj"))
    .settings(commonSettings: _*)
    .settings(name := s"async-http-client-backend-$proj")
    .dependsOn(asyncHttpClientBackend)

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
        f2sReactiveStream
      )
    )
    .dependsOn(catsJVM % "compile->compile;test->test")

//-- okhttp
lazy val okhttpBackend: Project = (project in file("okhttp-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "okhttp-backend",
    libraryDependencies ++= Seq(
      okHttp
    )
  )
  .dependsOn(coreJVM % "compile->compile;test->test")

def okhttpBackendProject(proj: String): Project =
  Project(s"okhttpBackend${proj.capitalize}", file(s"okhttp-backend/$proj"))
    .settings(commonSettings: _*)
    .settings(name := s"okhttp-backend-$proj")
    .dependsOn(okhttpBackend)

lazy val okhttpMonixBackend: Project =
  okhttpBackendProject("monix")
    .dependsOn(monixJVM % "compile->compile;test->test")

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
      json4sNative,
      scalaTest
    )
  )
  .dependsOn(coreJVM)

lazy val braveBackend: Project = (project in file("metrics/brave-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "brave-backend",
    libraryDependencies ++= Seq(
      brave,
      braveInstrumentationHttp,
      braveInstrumentationHttpTest,
      scalaTest
    )
  )
  .dependsOn(coreJVM)

lazy val prometheusBackend: Project = (project in file("metrics/prometheus-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "prometheus-backend",
    libraryDependencies ++= Seq(
      simpleClient,
      scalaTest
    )
  )
  .dependsOn(coreJVM)
