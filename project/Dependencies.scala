import sbt._

object Dependencies {

  import Versions._

  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion

  val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion

  val akkaStreamProvided = "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion % "provided"

  val asyncHttpClient = "org.asynchttpclient" % "async-http-client" % asyncHttpClientVersion

  val brave = "io.zipkin.brave" % "brave" % braveVersion

  val braveInstrumentationHttp = "io.zipkin.brave" % "brave-instrumentation-http" % braveVersion

  val f2sReactiveStream = "com.github.zainab-ali" %% "fs2-reactive-streams" % f2sReactiveStreamVersion

  val json4sNative = "org.json4s" %% "json4s-native" % json4sNativeVersion

  val okHttp = "com.squareup.okhttp3" % "okhttp" % okHttpVersion

  val simpleClient = "io.prometheus" % "simpleclient" % simpleClientVersion

  val scalazConcurrent = "org.scalaz" %% "scalaz-concurrent" % scalazConcurrentVersion


  /**
    * Test libraries
    */

  val akkaHttpTest = akkaHttp % "test"

  val akkaHttpCorsTest = "ch.megard" %% "akka-http-cors" % akkaHttpCorsTestVersion % "test"

  val akkaStreamsTest = akkaStreams % "test"

  val braveInstrumentationHttpTest = "io.zipkin.brave" % "brave-instrumentation-http-tests" % braveVersion % "test"

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % "test"

  val scalaCompilerTest = "org.scala-lang" % "scala-compiler" % scalaCompilerVersion % "test"

  val sparkMd5Test = "org.webjars.npm" % "spark-md5" % sparkMd5TestVersion % "test"
}


object Versions {

  val scalaCompilerVersion = "2.12.6"

  val akkaHttpVersion = "10.1.1"

  val akkaStreamsVersion = "2.5.13"

  val asyncHttpClientVersion = "2.4.9"

  val braveVersion = "5.0.0"

  val catzVersion = "1.0.0-RC2"

  val circeVersion = "0.9.3"

  val f2sReactiveStreamVersion = "0.6.0"

  val json4sNativeVersion = "3.5.4"

  val monixVersion = "3.0.0-RC1"

  val okHttpVersion = "3.10.0"

  val simpleClientVersion = "0.4.0"

  val scalaJsDomVersion = "0.9.6"

  val scalazConcurrentVersion = "7.2.24"

  /**
    * Test library versions
    */

  val akkaHttpCorsTestVersion = "0.3.0"

  val scalaTestVersion = "3.0.5"

  val sparkMd5TestVersion = "3.0.0"

}
