package com.softwaremill.sttp.testing.streaming

import com.softwaremill.sttp._
import com.softwaremill.sttp.testing.{ConvertToFuture, ToFutureWrapper}
import org.scalatest.{AsyncFreeSpec, BeforeAndAfterAll, Matchers}

import scala.language.higherKinds

trait StreamingTest[R[_], S]
    extends AsyncFreeSpec
    with Matchers
    with ToFutureWrapper
    with BeforeAndAfterAll
    with StreamingTestExtensions[R, S] {

  protected def endpoint: String

  private val body = "streaming test"

  implicit def backend: SttpBackend[R, S]

  implicit def convertToFuture: ConvertToFuture[R]

  def bodyProducer(body: String): S

  def bodyConsumer(stream: S): R[String]

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

  "receive a mapped stream" in {
    sttp
      .post(uri"$endpoint/streaming/echo")
      .body(body)
      .response(asStream[S].map(s => (s, true)))
      .send()
      .toFuture()
      .flatMap { response =>
        val (stream, flag) = response.unsafeBody
        bodyConsumer(stream).toFuture().map((_, flag))
      }
      .map { responseBody =>
        responseBody shouldBe ((body, true))
      }
  }

  "receive a stream from an https site" in {
    val numChunks = 100
    val url = uri"https://httpbin.org/stream/$numChunks"

    sttp
    // of course, you should never rely on the internet being available
    // in tests, but that's so much easier than setting up an https
    // testing server
      .get(url)
      .response(asStream[S])
      .send()
      .toFuture()
      .flatMap { response =>
        bodyConsumer(response.unsafeBody).toFuture()
      }
      .map { responseBody =>
        val urlRegex = s""""${url.toString}"""".r

        urlRegex.findAllIn(responseBody).length shouldBe numChunks
      }
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }

}
