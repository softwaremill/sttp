package sttp.client.impl.fs2

import cats.effect.IO
import cats.instances.string._
import fs2.{Chunk, Stream}
import sttp.client.impl.cats.CatsTestBase
import sttp.client.testing.streaming.StreamingTest

trait Fs2StreamingTest extends StreamingTest[IO, Stream[IO, Byte]] with CatsTestBase {
  override def bodyProducer(chunks: Iterable[Array[Byte]]): Stream[IO, Byte] =
    Stream.fromIterator[IO](chunks.iterator).flatMap(arr => Stream.chunk(Chunk.array(arr)))

  override def bodyConsumer(stream: fs2.Stream[IO, Byte]): IO[String] =
    stream
      .through(fs2.text.utf8Decode)
      .compile
      .foldMonoid
}
