package sttp.client4.impl.zio

import zio._
import zio.stream._
import sttp.capabilities.zio.ZioStreams
import sttp.client4.StreamBackend
import sttp.client4.internal._
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.streaming.StreamingTest
import sttp.model.sse.ServerSentEvent

class FetchZioStreamingTest extends StreamingTest[Task, ZioStreams] with ZioTestBase {
  override val streams: ZioStreams = ZioStreams

  override val backend: StreamBackend[Task, ZioStreams] = FetchZioBackend()

  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): ZStream[Any, Throwable, Byte] =
    ZStream.fromChunks(chunks.map(Chunk.fromArray).toSeq: _*)

  override def bodyConsumer(stream: ZStream[Any, Throwable, Byte]): Task[String] =
    stream.runCollect.map(bytes => new String(bytes.toArray, Utf8))

  override def sseConsumer(stream: Stream[Throwable, Byte]): Task[List[ServerSentEvent]] =
    stream.viaFunction(ZioServerSentEvents.parse).runCollect.map(_.toList)

  override protected def supportsStreamingMultipartParts: Boolean = false
}
