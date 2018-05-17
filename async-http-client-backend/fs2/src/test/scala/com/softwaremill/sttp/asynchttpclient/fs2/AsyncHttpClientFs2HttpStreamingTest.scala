package com.softwaremill.sttp.asynchttpclient.fs2

import java.nio.ByteBuffer

import cats.effect.IO
import cats.instances.string._
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.testing.streaming.{StreamingTest, TestStreamingBackend}
import com.softwaremill.sttp.testing.ConvertToFuture
import fs2.{Chunk, Stream, text}
import scala.concurrent.Future

class AsyncHttpClientFs2HttpStreamingTest extends StreamingTest[IO, Stream[IO, ByteBuffer]] {

  override val testStreamingBackend: TestStreamingBackend[IO, Stream[IO, ByteBuffer]] =
    new AsyncHttpClientFs2TestStreamingBackend
}

class AsyncHttpClientFs2TestStreamingBackend extends TestStreamingBackend[IO, Stream[IO, ByteBuffer]] {

  override implicit val backend: SttpBackend[IO, Stream[IO, ByteBuffer]] =
    AsyncHttpClientFs2Backend[IO]()

  override implicit val convertToFuture: ConvertToFuture[IO] =
    new ConvertToFuture[IO] {
      override def toFuture[T](value: IO[T]): Future[T] =
        value.unsafeToFuture()
    }

  override def bodyProducer(body: String): Stream[IO, ByteBuffer] =
    Stream.emits(body.getBytes("utf-8")).map(b => ByteBuffer.wrap(Array(b)))

  override def bodyConsumer(stream: Stream[IO, ByteBuffer]): IO[String] =
    stream
      .map(bb => Chunk.array(bb.array))
      .through(text.utf8DecodeC)
      .compile
      .foldMonoid

}
