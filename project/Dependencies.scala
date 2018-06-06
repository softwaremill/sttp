import sbt._
import Keys._

object Dependencies {

  val scalaVer = "2.12.6"

  val monixVersion = "3.0.0-RC1"

  val circeVersion = "0.9.3"

  val braveVersion = "5.0.0"

  val akkaHttpVersion = "10.1.1"

  val akkaHttpClientVersion = "2.4.9"

  val akkaStreamsVersion = "2.5.13"

  val scalazConcurrentVersion = "7.2.24"

  val catzEffectVersion = "1.0.0-RC"

  val fs2ReactiveStreamsVersion = "0.6.0"

  val okHttpVersion = "3.10.0"

  val json4sVersion = "3.5.4"

  val simpleClientVersion = "0.4.0"

  val typeSafeScalaLoggerVersion = "3.8.0"

  val betterFilesVersion = "3.4.0"

  val logbackClassicVersion = "1.2.3"

  val scalaTestVersion = "3.0.5"

  val akkaHttpCorsTestVersion = "0.3.0"

  val sparkMd5TestVersion = "3.0.0"

  val monix = "io.monix" %% "monix" % monixVersion

  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion

  val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion

  val asyncHttpClient = "org.asynchttpclient" % "async-http-client" % akkaHttpClientVersion

  val circeCore = "io.circe" %% "circe-core" % circeVersion

  val circeParser = "io.circe" %% "circe-parser" % circeVersion

  val brave = "io.zipkin.brave" % "brave" % braveVersion

  val braveInstrumentationHttp = "io.zipkin.brave" % "brave-instrumentation-http" % braveVersion

  val scalazConcurrent = "org.scalaz" %% "scalaz-concurrent" % scalazConcurrentVersion

  val catsEffect = "org.typelevel" %% "cats-effect" % catzEffectVersion

  val fs2ReactiveStreams = "com.github.zainab-ali" %% "fs2-reactive-streams" % fs2ReactiveStreamsVersion

  val okHttp = "com.squareup.okhttp3" % "okhttp" % okHttpVersion

  val json4sNative = "org.json4s" %% "json4s-native" % json4sVersion

  val simpleClient = "io.prometheus" % "simpleclient" % simpleClientVersion

  val typesafeScalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % typeSafeScalaLoggerVersion

  val betterFiles = "com.github.pathikrit" %% "better-files" % betterFilesVersion

  val logbackClassic = "ch.qos.logback" % "logback-classic" % logbackClassicVersion

  /**
    * Libraries to be used in Test.........
    */

  val braveInstrumentationHttpTest = "io.zipkin.brave" % "brave-instrumentation-http-tests" % braveVersion

  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.5"

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion

  val akkaHttpCorsTest = "ch.megard" %% "akka-http-cors" % akkaHttpCorsTestVersion

  val sparkMd5Test = "org.webjars.npm" % "spark-md5" % sparkMd5TestVersion

}
