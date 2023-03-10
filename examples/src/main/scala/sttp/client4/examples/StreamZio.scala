package sttp.client4.examples

import sttp.capabilities.zio.ZioStreams
import sttp.client4._
import zio.Console._
import zio._
import zio.stream._
import sttp.client4.httpclient.zio.{HttpClientZioBackend, SttpClient, send}

object StreamZio extends ZIOAppDefault {
  def streamRequestBody: RIO[SttpClient, Unit] = {
    val stream: Stream[Throwable, Byte] = ZStream("Hello, world".getBytes.toIndexedSeq: _*)
    send(
      basicRequest
        .post(uri"https://httpbin.org/post")
        .streamBody(ZioStreams)(stream)
    ).flatMap { response => printLine(s"RECEIVED:\n${response.body}") }
  }

  def streamResponseBody: RIO[SttpClient, Unit] =
    send(
      basicRequest
        .post(uri"https://httpbin.org/post")
        .body("I want a stream!")
        .response(asStreamAlways(ZioStreams)(_.via(ZPipeline.utf8Decode).runFold("")(_ + _)))
    ).flatMap { response => printLine(s"RECEIVED:\n${response.body}") }

  override def run =
    (streamRequestBody *> streamResponseBody).provide(HttpClientZioBackend.layer())
}
