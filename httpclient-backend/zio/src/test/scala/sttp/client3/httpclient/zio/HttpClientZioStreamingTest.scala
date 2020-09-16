package sttp.client3.httpclient.zio

import sttp.capabilities.zio.BlockingZioStreams
import sttp.client3.SttpBackend
import sttp.client3.impl.zio.ZioTestBase
import sttp.client3.internal._
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.streaming.StreamingTest
import zio._
import zio.blocking.Blocking
import zio.stream._

class HttpClientZioStreamingTest extends StreamingTest[BlockingTask, BlockingZioStreams] with ZioTestBase {
  override val streams: BlockingZioStreams = BlockingZioStreams

  override val backend: SttpBackend[BlockingTask, BlockingZioStreams] =
    runtime.unsafeRun(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[BlockingTask] = convertZioBlockingTaskToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): ZStream[Blocking, Throwable, Byte] =
    Stream.fromChunks(chunks.map(Chunk.fromArray).toSeq: _*)

  override def bodyConsumer(stream: ZStream[Blocking, Throwable, Byte]): BlockingTask[String] =
    stream.runCollect.map(bytes => new String(bytes.toArray, Utf8))

  override protected def supportsStreamingMultipartParts: Boolean = false
}
