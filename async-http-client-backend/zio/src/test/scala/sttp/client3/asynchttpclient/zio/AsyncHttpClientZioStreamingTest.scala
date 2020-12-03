package sttp.client3.asynchttpclient.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import sttp.client3.impl.zio.ZioTestBase
import sttp.client3.internal._
import sttp.client3.sse.ServerSentEvent
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.streaming.StreamingTest
import zio.{Chunk, Task}
import zio.stream.Stream

class AsyncHttpClientZioStreamingTest extends StreamingTest[Task, ZioStreams] with ZioTestBase {
  override val streams: ZioStreams = ZioStreams

  override val backend: SttpBackend[Task, ZioStreams] =
    runtime.unsafeRun(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def bodyProducer(arrays: Iterable[Array[Byte]]): Stream[Throwable, Byte] =
    Stream.fromChunks(arrays.map(Chunk.fromArray).toSeq: _*)

  override def bodyConsumer(stream: Stream[Throwable, Byte]): Task[String] =
    stream.runCollect.map(bytes => new String(bytes.toArray, Utf8))

  override def sseConsumer(stream: Stream[Throwable, Byte]): Task[List[ServerSentEvent]] = ???

  override protected def supportsStreamingMultipartParts: Boolean = false
}
