package sttp.client4.examples

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.instances.string.*
import fs2.Stream
import fs2.text
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.*
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

object StreamFs2 extends IOApp:
  def streamRequestBody(backend: StreamBackend[IO, Fs2Streams[IO]]): IO[Unit] = {
    val stream: Stream[IO, Byte] = Stream.emits("Hello, world".getBytes)

    basicRequest
      .post(uri"https://httpbin.org/post")
      .streamBody(Fs2Streams[IO])(stream)
      .send(backend)
      .map(response => println(s"RECEIVED:\n${response.body}"))
  }

  def streamResponseBody(backend: StreamBackend[IO, Fs2Streams[IO]]): IO[Unit] =
    basicRequest
      .body("I want a stream!")
      .post(uri"https://httpbin.org/post")
      .response(asStreamAlways(Fs2Streams[IO])(_.chunks.through(text.utf8.decodeC).compile.foldMonoid))
      .send(backend)
      .map(response => println(s"RECEIVED:\n${response.body}"))

  val effect = HttpClientFs2Backend
    .resource[IO]()
    .use: backend =>
      streamRequestBody(backend).flatMap(_ => streamResponseBody(backend))

  override def run(args: List[String]): IO[ExitCode] = effect.map(_ => ExitCode.Success)
