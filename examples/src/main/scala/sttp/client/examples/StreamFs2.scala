package sttp.client.examples

object StreamFs2 extends App {
  import sttp.client._
  import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend

  import cats.effect.{ContextShift, IO}
  import cats.instances.string._
  import fs2.{Stream, text}

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  def streamRequestBody(implicit backend: SttpBackend[IO, Stream[IO, Byte], NothingT]): IO[Unit] = {
    val stream: Stream[IO, Byte] = Stream.emits("Hello, world".getBytes)

    basicRequest
      .streamBody(stream)
      .post(uri"https://httpbin.org/post")
      .send()
      .map { response => println(s"RECEIVED:\n${response.body}") }
  }

  def streamResponseBody(implicit backend: SttpBackend[IO, Stream[IO, Byte], NothingT]): IO[Unit] = {
    basicRequest
      .body("I want a stream!")
      .post(uri"https://httpbin.org/post")
      .response(asStreamAlways[Stream[IO, Byte]])
      .send()
      .flatMap { response =>
        response.body
          .chunks
          .through(text.utf8DecodeC)
          .compile
          .foldMonoid
      }
      .map { body => println(s"RECEIVED:\n$body") }
  }

  val effect = AsyncHttpClientFs2Backend[IO]().flatMap { implicit backend =>
    streamRequestBody.flatMap(_ => streamResponseBody).guarantee(backend.close())
  }

  effect.unsafeRunSync()
}
