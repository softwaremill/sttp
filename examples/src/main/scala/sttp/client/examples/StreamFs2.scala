package sttp.client.examples

object StreamFs2 extends App {
  import sttp.client._
  import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend

  import java.nio.ByteBuffer
  import cats.effect.{ContextShift, IO}
  import cats.instances.string._
  import fs2.{Stream, Chunk, text}

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.Implicits.global)

  def streamRequestBody(implicit backend: SttpBackend[IO, Stream[IO, ByteBuffer], NothingT]): IO[Unit] = {
    val stream: Stream[IO, ByteBuffer] = Stream.emits(List("Hello, ".getBytes, "world".getBytes)).map(ByteBuffer.wrap)

    basicRequest
      .streamBody(stream)
      .post(uri"https://httpbin.org/post")
      .send()
      .map { response => println(s"RECEIVED:\n${response.body}") }
  }

  def streamResponseBody(implicit backend: SttpBackend[IO, Stream[IO, ByteBuffer], NothingT]): IO[Unit] = {
    basicRequest
      .body("I want a stream!")
      .post(uri"https://httpbin.org/post")
      .response(asStreamAlways[Stream[IO, ByteBuffer]])
      .send()
      .flatMap { response =>
        response.body
          .map(bb => Chunk.array(bb.array))
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
