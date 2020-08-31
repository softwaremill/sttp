package sttp.client.examples

import sttp.capabilities.zio.ZioStreams
import sttp.client._
import sttp.client.asynchttpclient.zio.{AsyncHttpClientZioBackend, SttpClient}
import zio._
import zio.console._
import zio.stream._

object StreamZio extends App {
  def streamRequestBody: RIO[Console with SttpClient, Unit] = {
    val stream: Stream[Throwable, Byte] = Stream("Hello, world".getBytes: _*)

    SttpClient
      .send(
        basicRequest
          .streamBody(ZioStreams)(stream)
          .post(uri"https://httpbin.org/post")
      )
      .flatMap { response => putStrLn(s"RECEIVED:\n${response.body}") }
  }

  def streamResponseBody: RIO[Console with SttpClient, Unit] = {
    SttpClient
      .send(
        basicRequest
          .body("I want a stream!")
          .post(uri"https://httpbin.org/post")
          .response(asStreamAlways(ZioStreams)(_.transduce(Transducer.utf8Decode).fold("")(_ + _)))
      )
      .flatMap { response => putStrLn(s"RECEIVED:\n${response.body}") }
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    (streamRequestBody *> streamResponseBody)
      .provideCustomLayer(AsyncHttpClientZioBackend.layer())
      .exitCode
  }
}
