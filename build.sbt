import Dependencies._

val commonSettings = Seq(
  organization := "com.softwaremill.sttp",
  scalaVersion := scalaVer,
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
  // silence transitive eviction warnings
  evictionWarningOptions in update := EvictionWarningOptions.default
    .withWarnTransitiveEvictions(false)
)

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "sttp")
  .aggregate(
    core,
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
    tests
  )

lazy val core: Project = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      scalaCheck % "test",
      scalaTest
    )
  )

lazy val akkaHttpBackend: Project = (project in file("akka-http-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "akka-http-backend",
    libraryDependencies ++= Seq(
      akkaHttp,
      // provided as we don't want to create a transitive dependency on a specific streams version,
      // just as akka-http doesn't
      akkaStreams % "provided"
    )
  ) dependsOn core

lazy val asyncHttpClientBackend: Project = (project in file(
  "async-http-client-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-backend",
    libraryDependencies ++= Seq(
      asyncHttpClient
    )
  ) dependsOn core

lazy val asyncHttpClientFutureBackend: Project = (project in file(
  "async-http-client-backend/future"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-backend-future"
  ) dependsOn asyncHttpClientBackend

lazy val asyncHttpClientScalazBackend: Project = (project in file(
  "async-http-client-backend/scalaz"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-backend-scalaz",
    libraryDependencies ++= Seq(
      scalazConcurrent
    )
  ) dependsOn asyncHttpClientBackend

lazy val asyncHttpClientMonixBackend: Project = (project in file(
  "async-http-client-backend/monix"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-backend-monix",
    libraryDependencies ++= Seq(monix)
  ) dependsOn asyncHttpClientBackend

lazy val asyncHttpClientCatsBackend: Project = (project in file(
  "async-http-client-backend/cats"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-backend-cats",
    libraryDependencies ++= Seq(
      catsEffect
    )
  ) dependsOn asyncHttpClientBackend

lazy val asyncHttpClientFs2Backend: Project = (project in file(
  "async-http-client-backend/fs2"))
  .settings(commonSettings: _*)
  .settings(
    name := "async-http-client-backend-fs2",
    libraryDependencies ++= Seq(
      fs2ReactiveStreams
    )
  ) dependsOn asyncHttpClientBackend

lazy val okhttpBackend: Project = (project in file("okhttp-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "okhttp-backend",
    libraryDependencies ++= Seq(
      okHttp
    )
  ) dependsOn core

lazy val okhttpMonixBackend: Project = (project in file("okhttp-backend/monix"))
  .settings(commonSettings: _*)
  .settings(
    name := "okhttp-backend-monix",
    libraryDependencies ++= Seq(monix)
  ) dependsOn okhttpBackend

lazy val circe: Project = (project in file("json/circe"))
  .settings(commonSettings: _*)
  .settings(
    name := "circe",
    libraryDependencies ++= Seq(
      circeCore,
      circeParser,
      scalaTest % "test"
    )
  ) dependsOn core

lazy val json4s: Project = (project in file("json/json4s"))
  .settings(commonSettings: _*)
  .settings(
    name := "json4s",
    libraryDependencies ++= Seq(
      json4sNative,
      scalaTest % "test"
    )
  ) dependsOn core

lazy val braveBackend: Project = (project in file("metrics/brave-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "brave-backend",
    libraryDependencies ++= Seq(
      brave,
      braveInstrumentationHttp,
      braveInstrumentationHttpTest % "test",
      scalaTest % "test"
    )
  ).dependsOn(core)

lazy val prometheusBackend: Project = (project in file("metrics/prometheus-backend"))
  .settings(commonSettings: _*)
  .settings(
    name := "prometheus-backend",
    libraryDependencies ++= Seq(
      simpleClient,
      scalaTest % "test"
    )
  )
  .dependsOn(core)

lazy val tests: Project = (project in file("tests"))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    name := "tests",
    libraryDependencies ++= Seq(
      akkaHttp,
      scalaTest,
      typesafeScalaLogging,
      betterFiles,
      logbackClassic,
      "org.scala-lang" % "scala-compiler" % scalaVersion.value
    ).map(_ % "test"),
    libraryDependencies += akkaStreams,
  ) dependsOn (core, akkaHttpBackend, asyncHttpClientFutureBackend, asyncHttpClientScalazBackend,
asyncHttpClientMonixBackend, asyncHttpClientCatsBackend, asyncHttpClientFs2Backend, okhttpMonixBackend)
