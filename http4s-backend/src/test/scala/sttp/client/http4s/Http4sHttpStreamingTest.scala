package sttp.client.http4s

import cats.effect.{ContextShift, IO}
import cats.instances.string._
import fs2.{Chunk, Stream, text}
import sttp.client.SttpBackend
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.streaming.StreamingTest

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class Http4sHttpStreamingTest extends StreamingTest[IO, Stream[IO, Byte]] {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(global)
  override implicit val backend: SttpBackend[IO, Stream[IO, Byte]] = TestHttp4sBackend()
  override implicit val convertToFuture: ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }

  override def bodyProducer(chunks: Iterable[Array[Byte]]): Stream[IO, Byte] =
    Stream.chunk(Chunk.concatBytes(chunks.toSeq.map(Chunk.array)))

  override def bodyConsumer(stream: Stream[IO, Byte]): IO[String] =
    stream
      .through(text.utf8Decode)
      .compile
      .foldMonoid
}
