package sttp.client.impl.fs2

import java.nio.ByteBuffer

import cats.effect.IO
import cats.implicits._
import fs2.{Chunk, Stream, text}
import sttp.client.impl.cats.CatsTestBase
import sttp.client.testing.streaming.StreamingTest

trait Fs2ByteBufferStreamingTest extends StreamingTest[IO, Stream[IO, ByteBuffer]] with CatsTestBase {

  override def bodyProducer(chunks: Iterable[Array[Byte]]): Stream[IO, ByteBuffer] =
    Stream.emits(chunks.toSeq).map(ByteBuffer.wrap)

  override def bodyConsumer(stream: Stream[IO, ByteBuffer]): IO[String] =
    stream
      .map(Chunk.ByteBuffer(_))
      .through(text.utf8DecodeC)
      .compile
      .foldMonoid
}
