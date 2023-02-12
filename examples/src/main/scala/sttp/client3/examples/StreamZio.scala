package sttp.client3.examples

import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import zio.Console._
import zio._
import zio.stream._
import sttp.client3.httpclient.zio.{HttpClientZioBackend, send}

object StreamZio extends ZIOAppDefault {
  def streamRequestBody(implicit layer: ZLayer[Any, Throwable, SttpBackend[Task, ZioStreams]]): Task[Unit] = {
    val stream: Stream[Throwable, Byte] = ZStream("Hello, world".getBytes: _*)
    send(
      basicRequest
        .streamBody(ZioStreams)(stream)
        .post(uri"https://httpbin.org/post")
    )
      .provideLayer(layer)
      .flatMap { response => printLine(s"RECEIVED:\n${response.body}") }
  }

  def streamResponseBody(implicit layer: ZLayer[Any, Throwable, SttpBackend[Task, ZioStreams]]): Task[Unit] = {
    send(
      basicRequest
        .body("I want a stream!")
        .post(uri"https://httpbin.org/post")
        .response(asStreamAlways(ZioStreams)(_.via(ZPipeline.utf8Decode).runFold("")(_ + _)))
    )
      .provideLayer(layer)
      .flatMap { response => printLine(s"RECEIVED:\n${response.body}") }
  }

  override def run = {
    implicit val layer = HttpClientZioBackend.layer()
    (streamRequestBody *> streamResponseBody).provide(HttpClientZioBackend.layer())
  }
}
