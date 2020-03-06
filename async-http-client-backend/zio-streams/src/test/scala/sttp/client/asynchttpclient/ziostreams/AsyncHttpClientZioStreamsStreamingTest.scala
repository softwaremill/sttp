package sttp.client.asynchttpclient.ziostreams

import java.nio.ByteBuffer

import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.zio._
import sttp.client.internal._
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.streaming.StreamingTest
import zio.Task
import zio.stream.Stream

class AsyncHttpClientZioStreamsStreamingTest extends StreamingTest[Task, Stream[Throwable, ByteBuffer]] {

  override implicit val backend: SttpBackend[Task, Stream[Throwable, ByteBuffer], NothingT] =
    runtime.unsafeRun(AsyncHttpClientZioStreamsBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioIoToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): Stream[Throwable, ByteBuffer] =
    Stream.apply(chunks.map(ByteBuffer.wrap).toSeq: _*)

  override def bodyConsumer(stream: Stream[Throwable, ByteBuffer]): Task[String] =
    stream.fold(ByteBuffer.allocate(0))(concatByteBuffers).map(_.array()).map(new String(_, Utf8))
}
