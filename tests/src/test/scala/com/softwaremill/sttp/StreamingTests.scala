package com.softwaremill.sttp

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.softwaremill.sttp.streaming._
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.language.higherKinds

class StreamingTests
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with StrictLogging
    with TestHttpServer
    with ForceWrapped {

  override val serverRoutes: Route =
    path("echo") {
      post {
        parameterMap { _ =>
          entity(as[String]) { body: String =>
            complete(body)
          }
        }
      }
    }

  override def port = 51824

  val body = "streaming test"

  var closeBackends: List[() => Unit] = Nil

  runTests("Akka Http", new AkkaHttpStreamingTests(actorSystem))
  runTests("Monix Async Http Client", new AsyncHttpClientMonixStreamingTests)
  runTests("Monix OkHttp", new OkHttpMonixStreamingTests)
  runTests("fs2 Async Http Client", new AsyncHttpClientFs2StreamingTests)

  def runTests[R[_], S](name: String, testStreamingBackend: TestStreamingBackend[R, S]): Unit = {
    import testStreamingBackend._

    closeBackends = (() => backend.close()) :: closeBackends

    name should "stream request body" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .streamBody(bodyProducer(body))
        .send()
        .force()

      response.unsafeBody shouldBe body
    }

    it should "receive a stream" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .body(body)
        .response(asStream[S])
        .send()
        .force()

      bodyConsumer(response.unsafeBody).force() shouldBe body
    }

    it should "receive a stream from an https site" in {
      val response = sttp
      // of course, you should never rely on the internet being available
      // in tests, but that's so much easier than setting up an https
      // testing server
        .get(uri"https://softwaremill.com")
        .response(asStream[S])
        .send()
        .force()

      bodyConsumer(response.unsafeBody).force() should include("</div>")
    }
  }

  override protected def afterAll(): Unit = {
    closeBackends.foreach(_())
    super.afterAll()
  }

}
