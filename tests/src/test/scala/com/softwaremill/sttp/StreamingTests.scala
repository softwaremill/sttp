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

  var closeHandlers: List[() => Unit] = Nil

  runTests("Akka Http", new AkkaStreamingTests(actorSystem))
  runTests("Monix Async Http Client", new MonixAHCStreamingTests)
  runTests("Monix OkHttp", new MonixOKHStreamingTests)
  runTests("fs2 Async Http Client", new Fs2StreamingTests)

  def runTests[R[_], S](
      name: String,
      testStreamingHandler: TestStreamingHandler[R, S]): Unit = {
    import testStreamingHandler._

    closeHandlers = handler.close _ :: closeHandlers

    name should "stream request body" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .streamBody(bodyProducer(body))
        .send()
        .force()

      response.body shouldBe body
    }

    it should "receive a stream" in {
      val response = sttp
        .post(uri"$endpoint/echo")
        .body(body)
        .response(asStream[S])
        .send()
        .force()

      bodyConsumer(response.body).force() shouldBe body
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

      bodyConsumer(response.body).force() should include("</div>")
    }
  }

  override protected def afterAll(): Unit = {
    closeHandlers.foreach(_())
    super.afterAll()
  }

}
