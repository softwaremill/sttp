package sttp.client4.httpclient.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client4.StreamBackend
import sttp.client4.impl.zio.{ZioServerSentEvents, ZioTestBase}
import sttp.client4.internal._
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.streaming.StreamingTest
import sttp.model.sse.ServerSentEvent
import zio._
import zio.stream._

class HttpClientZioStreamingTest extends StreamingTest[Task, ZioStreams] with ZioTestBase {
  override val streams: ZioStreams = ZioStreams

  override val backend: StreamBackend[Task, ZioStreams] =
    unsafeRunSyncOrThrow(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): ZStream[Any, Throwable, Byte] =
    ZStream.fromChunks(chunks.map(Chunk.fromArray).toSeq: _*)

  override def bodyConsumer(stream: ZStream[Any, Throwable, Byte]): Task[String] =
    stream.runCollect.map(bytes => new String(bytes.toArray, Utf8))

  override def sseConsumer(stream: Stream[Throwable, Byte]): Task[List[ServerSentEvent]] =
    stream.viaFunction(ZioServerSentEvents.parse).runCollect.map(_.toList)
}
