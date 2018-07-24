// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.{CrossType, crossProject}
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import com.softwaremill.Publish.Release.updateVersionInDocs

lazy val testServerPort = settingKey[Int]("Port to run the http test server on (used by JS tests)")
lazy val startTestServer = taskKey[Unit]("Start a http server used by tests (used by JS tests)")

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp",
  scalafmtOnCompile := true,
  // cross-release doesn't work when subprojects have different cross versions
  // work-around from https://github.com/sbt/sbt-release/issues/214
  releaseProcess := Seq(
    checkSnapshotDependencies,
    inquireVersions,
    // publishing locally so that the pgp password prompt is displayed early
    // in the process
    releaseStepCommandAndRemaining("+publishLocalSigned"),
    releaseStepCommandAndRemaining("+clean"),
    releaseStepCommandAndRemaining("+test"),
    setReleaseVersion,
    updateVersionInDocs(organization.value),
    commitReleaseVersion,
    tagRelease,
    releaseStepCommandAndRemaining("+publish"),
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges
  )
)

val commonJvmJsSettings = commonSettings ++ Seq(
  scalaVersion := "2.12.6",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.12")
)

val commonJvmSettings = commonJvmJsSettings

val commonJsSettings = commonJvmJsSettings ++ Seq(
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

val commonNativeSettings = commonSettings ++ Seq(
  organization := "com.softwaremill.sttp",
  scalaVersion := "2.11.12",
  crossScalaVersions := Seq("2.11.12"),
  nativeLinkStubs := true
)

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

val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.1.3"
val akkaStreams = "com.typesafe.akka" %% "akka-stream" % "2.5.13"

val scalaTestVersion = "3.0.5"
val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion

lazy val rootProjectAggregates: Seq[ProjectReference] = if (sys.env.isDefinedAt("STTP_NATIVE")) {
  println("[info] STTP_NATIVE defined, including sttp-native in the aggregate projects")
  List(rootJVM, rootJS, rootNative)
} else {
  println("[info] STTP_NATIVE *not* defined, *not* including sttp-native in the aggregate projects")
  List(rootJVM, rootJS)
}

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  // setting version to 2.11 so that cross-releasing works. It's the only version supported by all modules.
  .settings(skip in publish := true, name := "sttp", scalaVersion := "2.11.12")
  .aggregate(rootProjectAggregates: _*)

lazy val rootJVM = project
  .in(file(".jvm"))
  .settings(commonJvmJsSettings: _*)
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
  .settings(commonJvmJsSettings: _*)
  .settings(skip in publish := true, name := "sttpJS")
  .aggregate(coreJS, catsJS, monixJS, circeJS)

lazy val rootNative = project
  .in(file(".native"))
  .settings(commonNativeSettings: _*)
  .settings(skip in publish := true, name := "sttpNative")
  .aggregate(coreNative)

lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .jvmSettings(commonJvmSettings: _*)
  .jsSettings(commonJsSettings: _*)
  .nativeSettings(commonNativeSettings: _*)
  .jvmSettings(
    libraryDependencies ++= Seq(
      scalaTest % "test"
    )
  )
  .settings(
    name := "core",
    publishArtifact in Test := true // allow implementations outside of this repo
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.6",
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test"
    ),
    jsDependencies ++= Seq(
      "org.webjars.npm" % "spark-md5" % "3.0.0" % "test" / "spark-md5.js" minified "spark-md5.min.js"
    )
  )
  .jsSettings(browserTestSettings)
  .jsSettings(testServerSettings(Test))
  .nativeSettings(testServerSettings(Test))
  .nativeSettings(
    libraryDependencies ++= Seq(
      "org.scala-native" %%% "test-interface" % "0.3.0",
      "org.scalatest" %%% "scalatest" % "3.2.0-SNAP10" % "test"
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      akkaHttp % "test",
      "ch.megard" %% "akka-http-cors" % "0.3.0" % "test",
      akkaStreams % "test",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "test"
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
lazy val coreNative = core.native

//----- implementations
lazy val cats = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("implementations/cats"))
  .jvmSettings(commonJvmSettings: _*)
  .jsSettings(commonJsSettings: _*)
  .settings(
    name := "cats",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq("org.typelevel" %%% "cats-effect" % "1.0.0-RC2")
  )
lazy val catsJS = cats.js.dependsOn(coreJS % "compile->compile;test->test")
lazy val catsJVM = cats.jvm.dependsOn(coreJVM % "compile->compile;test->test")

lazy val monix = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("implementations/monix"))
  .jvmSettings(commonJvmSettings: _*)
  .jsSettings(commonJsSettings: _*)
  .jsSettings(testServerSettings(Test))
  .settings(
    name := "monix",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq("io.monix" %%% "monix" % "3.0.0-RC1")
  )
lazy val monixJS = monix.js.dependsOn(coreJS % "compile->compile;test->test")
lazy val monixJVM = monix.jvm.dependsOn(coreJVM % "compile->compile;test->test")

lazy val scalaz: Project = (project in file("implementations/scalaz"))
  .settings(commonJvmSettings: _*)
  .settings(
    name := "scalaz",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq("org.scalaz" %% "scalaz-concurrent" % "7.2.25")
  )
  .dependsOn(coreJVM % "compile->compile;test->test")

//----- backends
//-- akka
lazy val akkaHttpBackend: Project = (project in file("akka-http-backend"))
  .settings(commonJvmSettings: _*)
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
    .settings(commonJvmSettings: _*)
    .settings(
      name := "async-http-client-backend",
      libraryDependencies ++= Seq(
        "org.asynchttpclient" % "async-http-client" % "2.5.2"
      )
    )
    .dependsOn(coreJVM % "compile->compile;test->test")

def asyncHttpClientBackendProject(proj: String): Project = {
  Project(s"asyncHttpClientBackend${proj.capitalize}", file(s"async-http-client-backend/$proj"))
    .settings(commonJvmSettings: _*)
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
        "com.github.zainab-ali" %% "fs2-reactive-streams" % "0.6.0"
      )
    )
    .dependsOn(catsJVM % "compile->compile;test->test")

//-- okhttp
lazy val okhttpBackend: Project = (project in file("okhttp-backend"))
  .settings(commonJvmSettings: _*)
  .settings(
    name := "okhttp-backend",
    libraryDependencies ++= Seq(
      "com.squareup.okhttp3" % "okhttp" % "3.11.0"
    )
  )
  .dependsOn(coreJVM % "compile->compile;test->test")

def okhttpBackendProject(proj: String): Project = {
  Project(s"okhttpBackend${proj.capitalize}", file(s"okhttp-backend/$proj"))
    .settings(commonJvmSettings: _*)
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
  .jvmSettings(commonJvmSettings: _*)
  .jsSettings(commonJsSettings: _*)
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
  .settings(commonJvmSettings: _*)
  .settings(
    name := "json4s",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-native" % "3.5.4",
      "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
    )
  )
  .dependsOn(coreJVM)

lazy val braveVersion = "5.1.2"

lazy val braveBackend: Project = (project in file("metrics/brave-backend"))
  .settings(commonJvmSettings: _*)
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
  .settings(commonJvmSettings: _*)
  .settings(
    name := "prometheus-backend",
    libraryDependencies ++= Seq(
      "io.prometheus" % "simpleclient" % "0.4.0",
      scalaTest % "test"
    )
  )
  .dependsOn(coreJVM)
