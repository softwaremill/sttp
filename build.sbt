// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.{CrossType, crossProject}
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import com.softwaremill.Publish.Release.updateVersionInDocs

val scala2_11 = "2.11.12"
val scala2_12 = "2.12.9"
val scala2_13 = "2.13.0"

lazy val testServerPort = settingKey[Int]("Port to run the http test server on (used by JS tests)")
lazy val startTestServer = taskKey[Unit]("Start a http server used by tests (used by JS tests)")
lazy val is2_11 = settingKey[Boolean]("Is the scala version 2.11.")
lazy val is2_11_or_2_12 = settingKey[Boolean]("Is the scala version 2.11 or 2.12.")

val silencerVersion = "1.4.4"

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp.client",
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
    releaseStepCommandAndRemaining("+publishSigned"),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  ),
  is2_11 := scalaVersion.value.startsWith("2.11."),
  is2_11_or_2_12 := scalaVersion.value.startsWith("2.11.") || scalaVersion.value.startsWith("2.12."),
  libraryDependencies ++= Seq(
    compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
  )
)

// an ugly work-around for https://github.com/sbt/sbt/issues/3465
// even if a project is 2.11-only, we fake that it's also 2.12/2.13-compatible
val only2_11settings = Seq(
  publishArtifact := is2_11.value,
  skip := !is2_11.value,
  skip in publish := !is2_11.value,
  libraryDependencies := (if (is2_11.value) libraryDependencies.value else Nil)
)

val only2_11_and_2_12_settings = Seq(
  publishArtifact := is2_11_or_2_12.value,
  skip := !is2_11_or_2_12.value,
  skip in publish := !is2_11_or_2_12.value,
  libraryDependencies := (if (is2_11_or_2_12.value) libraryDependencies.value else Nil)
)

val commonJvmJsSettings = commonSettings ++ Seq(
  scalaVersion := scala2_11,
  crossScalaVersions := Seq(scalaVersion.value, scala2_12, scala2_13)
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
)

val commonNativeSettings = commonSettings ++ Seq(
  organization := "com.softwaremill.sttp.client",
  scalaVersion := scala2_11,
  crossScalaVersions := Seq(scala2_11),
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

val circeVersion: Option[(Long, Long)] => String = {
  case Some((2, 11)) => "0.11.1"
  case _             => "0.12.1"
}

val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.1.10"
val akkaStreams = "com.typesafe.akka" %% "akka-stream" % "2.5.25"

val scalaTestVersion = "3.0.8"
val scalaTestNativeVersion = "3.2.0-SNAP10"
val scalaNativeTestInterfaceVersion = "0.3.9"
val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

lazy val rootProjectAggregates: Seq[ProjectReference] = if (sys.env.isDefinedAt("STTP_NATIVE")) {
  println("[info] STTP_NATIVE defined, including sttp-native in the aggregate projects")
  List(rootJVM, rootJS, rootNative)
} else {
  println("[info] STTP_NATIVE *not* defined, *not* including sttp-native in the aggregate projects")
  List(rootJVM, rootJS)
}

val compileAndTest = "compile->compile;test->test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  // setting version to 2.11 so that cross-releasing works. Don't ask why.
  .settings(skip in publish := true, name := "sttp", scalaVersion := scala2_11, crossScalaVersions := Seq())
  .aggregate(rootProjectAggregates: _*)

lazy val rootJVM = project
  .in(file(".jvm"))
  .settings(commonJvmJsSettings: _*)
  .settings(skip in publish := true, name := "sttpJVM")
  .aggregate(
    modelJVM,
    coreJVM,
    catsJVM,
    monixJVM,
    scalaz,
    zio,
    // might fail due to // https://github.com/akka/akka-http/issues/1930
    akkaHttpBackend,
    asyncHttpClientBackend,
    asyncHttpClientFutureBackend,
    asyncHttpClientScalazBackend,
    asyncHttpClientZioBackend,
    asyncHttpClientZioStreamsBackend,
    asyncHttpClientMonixBackend,
    asyncHttpClientCatsBackend,
    asyncHttpClientFs2Backend,
    okhttpBackend,
    okhttpMonixBackend,
    http4sBackend,
    jsonCommonJVM,
    circeJVM,
    json4s,
    sprayJson,
    playJsonJVM,
    braveBackend,
    prometheusBackend
  )

lazy val rootJS = project
  .in(file(".js"))
  .settings(commonJvmJsSettings: _*)
  .settings(skip in publish := true, name := "sttpJS")
  .aggregate(modelJS, coreJS, catsJS, monixJS, jsonCommonJS, circeJS, playJsonJS)

lazy val rootNative = project
  .in(file(".native"))
  .settings(commonNativeSettings: _*)
  .settings(skip in publish := true, name := "sttpNative")
  .aggregate(modelNative, coreNative)

lazy val model = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("model"))
  .settings(name := "model")
  .jvmSettings(commonJvmSettings: _*)
  .jsSettings(commonJsSettings: _*)
  .nativeSettings(commonNativeSettings: _*)
  .nativeSettings(only2_11settings)
  .jvmSettings(libraryDependencies ++= Seq(scalaTest % "test"))
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.7",
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test"
    )
  )
  .jsSettings(browserTestSettings)
  .nativeSettings(
    libraryDependencies ++= Seq(
      "org.scala-native" %%% "test-interface" % scalaNativeTestInterfaceVersion,
      "org.scalatest" %%% "scalatest" % scalaTestNativeVersion % "test"
    )
  )

lazy val modelJS = model.js
lazy val modelJVM = model.jvm
lazy val modelNative = model.native

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
      "org.scalatest" %%% "scalatest" % scalaTestNativeVersion % "test"
    )
  )
  .nativeSettings(only2_11settings)
  .jvmSettings(
    libraryDependencies ++= Seq(
      akkaHttp % "test",
      "ch.megard" %% "akka-http-cors" % "0.4.1" % "test",
      akkaStreams % "test",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "test"
    ),
    // the test server needs to be started before running any JS tests
    // `reStart` cannot be scoped so it can't be only added to Test
    mainClass in reStart := Some("sttp.client.testing.HttpServer"),
    reStartArgs in reStart := Seq(s"${(testServerPort in Test).value}"),
    fullClasspath in reStart := (fullClasspath in Test).value,
    testServerPort in Test := 51823,
    startTestServer in Test := reStart.toTask("").value
  )
lazy val coreJS = core.js.dependsOn(modelJS)
lazy val coreJVM = core.jvm.dependsOn(modelJVM)
lazy val coreNative = core.native.dependsOn(modelNative)

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
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % "2.0.0"
    )
  )
lazy val catsJS = cats.js.dependsOn(coreJS % compileAndTest)
lazy val catsJVM = cats.jvm.dependsOn(coreJVM % compileAndTest)

lazy val monix = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("implementations/monix"))
  .jvmSettings(commonJvmSettings: _*)
  .jvmSettings(
    libraryDependencies ++= Seq("io.monix" %% "monix-nio" % "0.0.5")
  )
  .jsSettings(commonJsSettings: _*)
  .jsSettings(browserTestSettings)
  .jsSettings(testServerSettings(Test))
  .settings(
    name := "monix",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq("io.monix" %%% "monix" % "3.0.0")
  )
lazy val monixJS = monix.js.dependsOn(coreJS % compileAndTest)
lazy val monixJVM = monix.jvm.dependsOn(coreJVM % compileAndTest)

lazy val zio: Project = (project in file("implementations/zio"))
  .settings(commonJvmSettings: _*)
  .settings(
    name := "zio",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "1.0.0-RC15"
    )
  )
  .dependsOn(coreJVM % compileAndTest)

lazy val scalaz: Project = (project in file("implementations/scalaz"))
  .settings(commonJvmSettings: _*)
  .settings(
    name := "scalaz",
    publishArtifact in Test := true,
    libraryDependencies ++= Seq("org.scalaz" %% "scalaz-concurrent" % "7.2.28")
  )
  .dependsOn(coreJVM % compileAndTest)

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
  .dependsOn(coreJVM % compileAndTest)

//-- async http client
lazy val asyncHttpClientBackend: Project =
  (project in file("async-http-client-backend"))
    .settings(commonJvmSettings: _*)
    .settings(
      name := "async-http-client-backend",
      libraryDependencies ++= Seq(
        "org.asynchttpclient" % "async-http-client" % "2.10.3"
      )
    )
    .dependsOn(coreJVM % compileAndTest)

def asyncHttpClientBackendProject(proj: String): Project = {
  Project(s"asyncHttpClientBackend${proj.capitalize}", file(s"async-http-client-backend/$proj"))
    .settings(commonJvmSettings: _*)
    .settings(name := s"async-http-client-backend-$proj")
    .dependsOn(asyncHttpClientBackend % compileAndTest)
}

lazy val asyncHttpClientFutureBackend: Project =
  asyncHttpClientBackendProject("future")
    .dependsOn(coreJVM % compileAndTest)

lazy val asyncHttpClientScalazBackend: Project =
  asyncHttpClientBackendProject("scalaz")
    .dependsOn(scalaz % compileAndTest)

lazy val asyncHttpClientZioBackend: Project =
  asyncHttpClientBackendProject("zio")
    .dependsOn(zio % compileAndTest)

lazy val asyncHttpClientZioStreamsBackend: Project =
  asyncHttpClientBackendProject("zio-streams")
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-streams" % "1.0.0-RC15",
        "dev.zio" %% "zio-interop-reactivestreams" % "1.0.3.2-RC1"
      )
    )
    .settings(only2_11_and_2_12_settings)
    .dependsOn(zio % compileAndTest)

lazy val asyncHttpClientMonixBackend: Project =
  asyncHttpClientBackendProject("monix")
    .dependsOn(monixJVM % compileAndTest)

lazy val asyncHttpClientCatsBackend: Project =
  asyncHttpClientBackendProject("cats")
    .dependsOn(catsJVM % compileAndTest)

val fs2Version = "2.0.1"
lazy val asyncHttpClientFs2Backend: Project =
  asyncHttpClientBackendProject("fs2")
    .settings(
      libraryDependencies ++= Seq(
        "co.fs2" %% "fs2-reactive-streams" % fs2Version,
        "co.fs2" %% "fs2-io" % fs2Version
      )
    )
    .dependsOn(catsJVM % compileAndTest)

//-- okhttp
lazy val okhttpBackend: Project = (project in file("okhttp-backend"))
  .settings(commonJvmSettings: _*)
  .settings(
    name := "okhttp-backend",
    libraryDependencies ++= Seq(
      "com.squareup.okhttp3" % "okhttp" % "4.2.1"
    )
  )
  .dependsOn(coreJVM % compileAndTest)

def okhttpBackendProject(proj: String): Project = {
  Project(s"okhttpBackend${proj.capitalize}", file(s"okhttp-backend/$proj"))
    .settings(commonJvmSettings: _*)
    .settings(name := s"okhttp-backend-$proj")
    .dependsOn(okhttpBackend)
}

lazy val okhttpMonixBackend: Project =
  okhttpBackendProject("monix")
    .dependsOn(monixJVM % compileAndTest)

//-- http4s
lazy val http4sBackend: Project = (project in file("http4s-backend"))
  .settings(commonJvmSettings: _*)
  .settings(
    name := "http4s-backend",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-client" % "0.20.10"
    )
  )
  .settings(only2_11_and_2_12_settings)
  .dependsOn(catsJVM, coreJVM % compileAndTest)

//----- json
lazy val jsonCommon = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("json/common"))
  .jvmSettings(commonJvmSettings: _*)
  .jsSettings(commonJsSettings: _*)
  .settings(
    name := "json-common"
  )

lazy val jsonCommonJVM = jsonCommon.jvm
lazy val jsonCommonJS = jsonCommon.js

lazy val circe = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("json/circe"))
  .jvmSettings(commonJvmSettings: _*)
  .jsSettings(commonJsSettings: _*)
  .settings(
    name := "circe",
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "io.circe" %%% "circe-core" % circeVersion(_),
      "io.circe" %%% "circe-parser" % circeVersion(_),
      _ => "org.scalatest" %%% "scalatest" % scalaTestVersion % "test"
    )
  )
  .jsSettings(only2_11_and_2_12_settings) // circe-js for 2.13 fails to run tests with a linking error
lazy val circeJS = circe.js.dependsOn(coreJS, jsonCommonJS)
lazy val circeJVM = circe.jvm.dependsOn(coreJVM, jsonCommonJVM)

lazy val json4sVersion = "3.6.7"

lazy val json4s: Project = (project in file("json/json4s"))
  .settings(commonJvmSettings: _*)
  .settings(
    name := "json4s",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-core" % json4sVersion,
      "org.json4s" %% "json4s-native" % json4sVersion % "test",
      "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
    )
  )
  .dependsOn(coreJVM, jsonCommonJVM)

lazy val sprayJson: Project = (project in file("json/spray-json"))
  .settings(commonJvmSettings: _*)
  .settings(
    name := "spray-json",
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-json" % "1.3.5",
      "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
    )
  )
  .dependsOn(coreJVM, jsonCommonJVM)

lazy val playJson = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("json/play-json"))
  .jsSettings(commonJsSettings: _*)
  .jvmSettings(commonJvmSettings: _*)
  .settings(
    name := "play-json",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % "2.7.4",
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test"
    )
  )
lazy val playJsonJS = playJson.js.dependsOn(coreJS, jsonCommonJS)
lazy val playJsonJVM = playJson.jvm.dependsOn(coreJVM, jsonCommonJVM)

lazy val braveVersion = "5.8.0"

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
      "io.prometheus" % "simpleclient" % "0.7.0",
      scalaTest % "test"
    )
  )
  .dependsOn(coreJVM)
