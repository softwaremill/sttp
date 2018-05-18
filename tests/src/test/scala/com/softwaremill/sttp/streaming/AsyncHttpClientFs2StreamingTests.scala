package com.softwaremill.sttp.streaming

import java.nio.ByteBuffer

import cats.effect._
import cats.instances.string._
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import com.softwaremill.sttp.impl.cats.convertCatsIOToFuture
import com.softwaremill.sttp.testing.streaming.{ConvertToFuture, TestStreamingBackend}
import fs2.{Chunk, Stream, text}

class AsyncHttpClientFs2StreamingTests extends TestStreamingBackend[IO, Stream[IO, ByteBuffer]] {

  override implicit val backend: SttpBackend[IO, Stream[IO, ByteBuffer]] =
    AsyncHttpClientFs2Backend[IO]()

  override implicit val convertToFuture: ConvertToFuture[IO] = convertCatsIOToFuture

  override def bodyProducer(body: String): Stream[IO, ByteBuffer] =
    Stream.emits(body.getBytes("utf-8").map(b => ByteBuffer.wrap(Array(b))))

  override def bodyConsumer(stream: Stream[IO, ByteBuffer]): IO[String] =
    stream
      .map(bb => Chunk.array(bb.array))
      .through(text.utf8DecodeC)
      .compile
      .foldMonoid

}
