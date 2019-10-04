package sttp.client.asynchttpclient.ziostreams

import java.nio.ByteBuffer

import sttp.client.SttpBackend
import sttp.client.impl.zio._
import sttp.client.internal._
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.streaming.StreamingTest
import zio.Task
import zio.stream.Stream

class AsyncHttpClientZioStreamsStreamingTest extends StreamingTest[Task, Stream[Throwable, ByteBuffer]] {

  override implicit val backend: SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    runtime.unsafeRunSync(AsyncHttpClientZioStreamsBackend(runtime)).getOrElse(c => throw c.squash)
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioIoToFuture

  override def bodyProducer(body: String): Stream[Throwable, ByteBuffer] =
    Stream.apply(body.getBytes(Utf8).grouped(10).map(ByteBuffer.wrap).toSeq: _*)

  override def bodyConsumer(stream: Stream[Throwable, ByteBuffer]): Task[String] =
    stream.fold(ByteBuffer.allocate(0))(concatByteBuffers).map(_.array()).map(new String(_, Utf8))
}
