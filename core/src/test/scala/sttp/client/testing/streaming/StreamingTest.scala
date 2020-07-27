package sttp.client.testing.streaming

import sttp.client._
import sttp.client.testing.{ConvertToFuture, ToFutureWrapper}
import org.scalatest.{BeforeAndAfterAll, SuiteMixin}
import org.scalatest.freespec.AsyncFreeSpecLike
import StreamingTest._
import sttp.client.internal.Utf8

import scala.language.higherKinds
import org.scalatest.matchers.should.Matchers
import sttp.client.testing.HttpTest.endpoint

// TODO: change to `extends AsyncFreeSpec` when https://github.com/scalatest/scalatest/issues/1802 is fixed
abstract class StreamingTest[F[_], S]
    extends SuiteMixin
    with AsyncFreeSpecLike
    with Matchers
    with ToFutureWrapper
    with BeforeAndAfterAll
    with StreamingTestExtensions[F, S] {

  val streams: Streams[S]

  implicit def backend: SttpBackend[F, S, NothingT]

  implicit def convertToFuture: ConvertToFuture[F]

  def bodyProducer(chunks: Iterable[Array[Byte]]): streams.BinaryStream

  private def stringBodyProducer(body: String): streams.BinaryStream =
    bodyProducer(body.getBytes(Utf8).grouped(10).toIterable)

  def bodyConsumer(stream: streams.BinaryStream): F[String]

  "stream request body" in {
    basicRequest
      .post(uri"$endpoint/streaming/echo")
      .streamBody(streams)(stringBodyProducer(Body))
      .send()
      .toFuture()
      .map { response =>
        response.body shouldBe Right(Body)
      }
  }

  "stream large request body" in {
    basicRequest
      .post(uri"$endpoint/streaming/echo")
      .streamBody(streams)(stringBodyProducer(Body))
      .send()
      .toFuture()
      .map { response =>
        response.body shouldBe Right(Body)
      }
  }

  "receive a stream" in {
    // TODO: for some reason these explicit types are needed in Dotty
    val r0: RequestT[Identity, String, Effect[F] with S] = basicRequest
      .post(uri"$endpoint/streaming/echo")
      .body(Body)
      .response(asStreamAlways(streams)(bodyConsumer(_)))
    r0.send()
      .toFuture()
      .map { response =>
        response.body shouldBe Body
      }
  }

  "receive a stream and consume it, if there's a processing error" in {
    // TODO: for some reason these explicit types are needed in Dotty
    val r0: RequestT[Identity, String, Effect[F] with S] = basicRequest
      .post(uri"$endpoint/streaming/echo")
      .body(Body)
      .response(asStreamAlways(streams)(bodyConsumer(_)))
    r0.send()
      .toFuture()
      .map { response =>
        response.body shouldBe Body
      }
  }

  "receive a stream (unsafe)" in {
    // TODO: for some reason these explicit types are needed in Dotty
    val r0: RequestT[Identity, streams.BinaryStream, S] = basicRequest
      .post(uri"$endpoint/streaming/echo")
      .body(Body)
      .response(asStreamUnsafeAlways(streams))
    r0.send()
      .toFuture()
      .flatMap { response =>
        bodyConsumer(response.body).toFuture()
      }
      .map { responseBody =>
        responseBody shouldBe Body
      }
  }

  "receive a large stream (unsafe)" in {
    // TODO: for some reason these explicit types are needed in Dotty
    val r0: RequestT[Identity, streams.BinaryStream, S] = basicRequest
      .post(uri"$endpoint/streaming/echo")
      .body(LargeBody)
      .response(asStreamUnsafeAlways(streams))
    r0.send()
      .toFuture()
      .flatMap { response =>
        bodyConsumer(response.body).toFuture()
      }
      .map { responseBody =>
        if (responseBody.length != LargeBody.length) {
          fail(s"Response body had length ${responseBody.length}, instead of ${LargeBody.length}, starts with: ${responseBody
            .take(512)}")
        } else {
          succeed
        }
      }
  }

  "receive a stream or error (unsafe)" in {
    // TODO: for some reason these explicit types are needed in Dotty
    val r0: RequestT[Identity, Either[String, streams.BinaryStream], S] = basicRequest
      .post(uri"$endpoint/streaming/echo")
      .body(Body)
      .response(asStreamUnsafe(streams))
    r0.send()
      .toFuture()
      .flatMap { response =>
        bodyConsumer(response.body.right.get).toFuture()
      }
      .map { responseBody =>
        responseBody shouldBe Body
      }
  }

  "receive a mapped stream (unsafe)" in {
    // TODO: for some reason these explicit types are needed in Dotty
    val r0: RequestT[Identity, (streams.BinaryStream, Boolean), S] = basicRequest
      .post(uri"$endpoint/streaming/echo")
      .body(Body)
      .response(asStreamUnsafeAlways(streams).map(s => (s, true)))
    r0
      .send()
      .toFuture()
      .flatMap { response =>
        val (stream, flag) = response.body
        bodyConsumer(stream).toFuture().map((_, flag))
      }
      .map { responseBody =>
        responseBody shouldBe ((Body, true))
      }
  }

  "receive a stream from an https site (unsafe)" in {
    val numChunks = 100
    val url = uri"https://httpbin.org/stream/$numChunks"

    // TODO: for some reason these explicit types are needed in Dotty
    val r0: RequestT[Identity, streams.BinaryStream, S] = basicRequest
      // of course, you should never rely on the internet being available
      // in tests, but that's so much easier than setting up an https
      // testing server
      .get(url)
      .response(asStreamUnsafeAlways(streams))

    r0
      .send()
      .toFuture()
      .flatMap { response =>
        bodyConsumer(response.body).toFuture()
      }
      .map { responseBody =>
        val urlRegex = s""""${url.toString}"""".r

        urlRegex.findAllIn(responseBody).length shouldBe numChunks
      }
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture()
    super.afterAll()
  }

}

object StreamingTest {
  val Body = "streaming test"
  val LargeBody: String = "x" * 4000000
}
