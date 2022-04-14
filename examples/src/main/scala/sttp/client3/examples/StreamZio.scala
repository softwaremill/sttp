package sttp.client3.examples

import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient, send}
import zio._
import zio.Console._
import zio.stream._

object StreamZio extends ZIOAppDefault {
  def streamRequestBody: RIO[SttpClient, Unit] = {
    val stream: Stream[Throwable, Byte] = Stream("Hello, world".getBytes: _*)

    send(
      basicRequest
        .streamBody(ZioStreams)(stream)
        .post(uri"https://httpbin.org/post")
    ).flatMap { response => printLine(s"RECEIVED:\n${response.body}") }
  }

  def streamResponseBody: RIO[SttpClient, Unit] = {
    send(
      basicRequest
        .body("I want a stream!")
        .post(uri"https://httpbin.org/post")
        .response(asStreamAlways(ZioStreams)(_.via(ZPipeline.utf8Decode).runFold("")(_ + _)))
    ).flatMap { response => printLine(s"RECEIVED:\n${response.body}") }
  }

  override def run = {
    (streamRequestBody *> streamResponseBody)
      .provide(AsyncHttpClientZioBackend.layer())
      .exitCode
  }
}
