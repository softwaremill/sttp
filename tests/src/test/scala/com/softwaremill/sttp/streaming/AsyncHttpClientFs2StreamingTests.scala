package com.softwaremill.sttp.streaming

import java.nio.ByteBuffer

import cats.effect._
import cats.implicits._
import com.softwaremill.sttp.asynchttpclient.fs2.AsyncHttpClientFs2Handler
import com.softwaremill.sttp.{ForceWrappedValue, SttpHandler}
import fs2._

class AsyncHttpClientFs2StreamingTests
    extends TestStreamingHandler[IO, Stream[IO, ByteBuffer]] {

  override implicit val handler: SttpHandler[IO, Stream[IO, ByteBuffer]] =
    AsyncHttpClientFs2Handler[IO]()

  override implicit val forceResponse: ForceWrappedValue[IO] =
    ForceWrappedValue.catsIo

  override def bodyProducer(body: String): Stream[IO, ByteBuffer] =
    Stream.emits(body.getBytes("utf-8").map(b => ByteBuffer.wrap(Array(b))))

  override def bodyConsumer(stream: Stream[IO, ByteBuffer]): IO[String] =
    stream
      .map(bb => Chunk.array(bb.array))
      .through(text.utf8DecodeC)
      .runFoldMonoid

}
