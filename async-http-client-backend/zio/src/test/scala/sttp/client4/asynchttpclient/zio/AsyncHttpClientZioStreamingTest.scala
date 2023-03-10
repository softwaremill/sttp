package sttp.client4.asynchttpclient.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client4.StreamBackend
import sttp.client4.impl.zio.{ZioServerSentEvents, ZioTestBase}
import sttp.client4.internal._
import sttp.model.sse.ServerSentEvent
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.streaming.StreamingTest
import zio.stream.{Stream, ZStream}
import zio.{Chunk, Task}

class AsyncHttpClientZioStreamingTest extends StreamingTest[Task, ZioStreams] with ZioTestBase {
  override val streams: ZioStreams = ZioStreams

  override val backend: StreamBackend[Task, ZioStreams] =
    unsafeRunSyncOrThrow(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def bodyProducer(arrays: Iterable[Array[Byte]]): Stream[Throwable, Byte] =
    ZStream.fromChunks(arrays.map(Chunk.fromArray).toSeq: _*)

  override def bodyConsumer(stream: Stream[Throwable, Byte]): Task[String] =
    stream.runCollect.map(bytes => new String(bytes.toArray, Utf8))

  override def sseConsumer(stream: Stream[Throwable, Byte]): Task[List[ServerSentEvent]] =
    stream.viaFunction(ZioServerSentEvents.parse).runCollect.map(_.toList)

  override protected def supportsStreamingMultipartParts: Boolean = false
}
