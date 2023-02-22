package sttp.client3.examples

import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.Console._
import zio._
import zio.stream._

object StreamZio extends ZIOAppDefault {
  def streamRequestBody(backend: StreamBackend[Task, ZioStreams]): Task[Unit] = {
    val stream: Stream[Throwable, Byte] = ZStream("Hello, world".getBytes: _*)

    basicRequest
      .post(uri"https://httpbin.org/post")
      .streamBody(ZioStreams)(stream)
      .send(backend)
      .flatMap { response => printLine(s"RECEIVED:\n${response.body}") }
  }

  def streamResponseBody(backend: StreamBackend[Task, ZioStreams]): Task[Unit] = {
    basicRequest
      .body("I want a stream!")
      .post(uri"https://httpbin.org/post")
      .response(asStreamAlways(ZioStreams)(_.via(ZPipeline.utf8Decode).runFold("")(_ + _)))
      .send(backend)
      .flatMap { response => printLine(s"RECEIVED:\n${response.body}") }
  }

  override def run =
    HttpClientZioBackend.scoped().flatMap { backend =>
      streamRequestBody(backend) *> streamResponseBody(backend)
    }
}
