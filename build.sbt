lazy val commonSettings = Seq(
  organization := "com.softwaremill.sttp",
  version := "0.1",
  scalaVersion := "2.12.2",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.8"),
  scalacOptions ++= Seq("-unchecked", "-deprecation"),
  scalafmtOnCompile := true,
  // Sonatype OSS deployment
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    val (name, url) =
      if (isSnapshot.value)
        ("snapshots", nexus + "content/repositories/snapshots")
      else ("releases", nexus + "service/local/staging/deploy/maven2")
    Some(name at url)
  },
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  publishMavenStyle := true,
  pomIncludeRepository := { _ =>
    false
  },
  pomExtra := (
    <scm>
      <url>git@github.com/softwaremill/sttp.git</url>
      <connection>scm:git:git@github.com/softwaremill/sttp.git</connection>
    </scm>
      <developers>
        <developer>
          <id>adamw</id>
          <name>Adam Warski</name>
          <url>http://www.warski.org</url>
        </developer>
      </developers>
  ),
  licenses := ("Apache2",
               new java.net.URL(
                 "http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil,
  homepage := Some(new java.net.URL("http://softwaremill.com/open-source"))
)

val akkaHttpVersion = "10.0.9"
val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "sttp")
  .aggregate(core, akkaHttpHandler, tests)

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
  ) dependsOn (core)

lazy val tests: Project = (project in file("tests"))
  .settings(commonSettings: _*)
  .settings(
    name := "tests",
    libraryDependencies ++= Seq(
      akkaHttp,
      scalaTest,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "com.github.pathikrit" %% "better-files" % "2.17.1",
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    ).map(_ % "test")
  ) dependsOn (core, akkaHttpHandler)
