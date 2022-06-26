package sttp.client3.httpclient.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import sttp.client3.impl.zio.{ZioServerSentEvents, ZioTestBase}
import sttp.client3.internal._
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.streaming.StreamingTest
import sttp.model.sse.ServerSentEvent
import zio._
import zio.stream._

class HttpClientZioStreamingTest extends StreamingTest[Task, ZioStreams] with ZioTestBase {
  override val streams: ZioStreams = ZioStreams

  override val backend: SttpBackend[Task, ZioStreams] =
    unsafeRun(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): ZStream[Any, Throwable, Byte] =
    ZStream.fromChunks(chunks.map(Chunk.fromArray).toSeq: _*)

  override def bodyConsumer(stream: ZStream[Any, Throwable, Byte]): Task[String] =
    stream.runCollect.map(bytes => new String(bytes.toArray, Utf8))

  override def sseConsumer(stream: Stream[Throwable, Byte]): Task[List[ServerSentEvent]] =
    stream.viaFunction(ZioServerSentEvents.parse).runCollect.map(_.toList)

  override protected def supportsStreamingMultipartParts: Boolean = false
}
