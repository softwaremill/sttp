package com.softwaremill.sttp.testing.streaming

import com.softwaremill.sttp._
import com.softwaremill.sttp.testing.ToFutureWrapper
import org.scalatest.{AsyncFreeSpec, BeforeAndAfterAll, Matchers}
import scala.language.higherKinds

trait StreamingTest[R[_], S] extends AsyncFreeSpec with Matchers with BeforeAndAfterAll with ToFutureWrapper {

  private val endpoint = "localhost:51823"
  private val body = "streaming test"

  val testStreamingBackend: TestStreamingBackend[R, S]
  import testStreamingBackend._

  "stream request body" in {
    sttp
      .post(uri"$endpoint/streaming/echo")
      .streamBody(bodyProducer(body))
      .send()
      .toFuture()
      .map { response =>
        response.unsafeBody shouldBe body
      }
  }

  "receive a stream" in {
    sttp
      .post(uri"$endpoint/streaming/echo")
      .body(body)
      .response(asStream[S])
      .send()
      .toFuture()
      .flatMap { response =>
        bodyConsumer(response.unsafeBody).toFuture()
      }
      .map { responseBody =>
        responseBody shouldBe body
      }
  }

  "receive a stream from an https site" in {
    sttp
    // of course, you should never rely on the internet being available
    // in tests, but that's so much easier than setting up an https
    // testing server
      .get(uri"https://softwaremill.com")
      .response(asStream[S])
      .send()
      .toFuture()
      .flatMap { response =>
        bodyConsumer(response.unsafeBody).toFuture()
      }
      .map { responseBody =>
        responseBody should include("</div>")
      }
  }

  override protected def afterAll(): Unit = {
    testStreamingBackend.backend.close()
    super.afterAll()
  }

}
