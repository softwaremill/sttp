package sttp.client3.armeria.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client3.armeria.ArmeriaWebClient
import sttp.client3.{SttpBackend, SttpBackendOptions}
import sttp.client3.impl.zio.{ZioServerSentEvents, ZioTestBase}
import sttp.client3.internal._
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.streaming.StreamingTest
import sttp.model.sse.ServerSentEvent
import zio.stream.{Stream, ZStream}
import zio.{Chunk, Task}

import java.time.Duration

class ArmeriaZioStreamingTest extends StreamingTest[Task, ZioStreams] with ZioTestBase {
  override val streams: ZioStreams = ZioStreams

  override val backend: SttpBackend[Task, ZioStreams] =
    unsafeRunSyncOrThrow(
      ArmeriaZioBackend.usingClient(
        // the default caused timeouts in SSE tests
        ArmeriaWebClient.newClient(SttpBackendOptions.Default, _.writeTimeout(Duration.ofMillis(0)))
      )
    )
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def bodyProducer(arrays: Iterable[Array[Byte]]): Stream[Throwable, Byte] =
    ZStream.fromChunks(arrays.map(Chunk.fromArray).toSeq: _*)

  override def bodyConsumer(stream: Stream[Throwable, Byte]): Task[String] =
    stream.runCollect.map(bytes => new String(bytes.toArray, Utf8))

  // TODO: consider if viaFunction is what we want
  override def sseConsumer(stream: Stream[Throwable, Byte]): Task[List[ServerSentEvent]] =
    stream.viaFunction(ZioServerSentEvents.parse).runCollect.map(_.toList)

  override protected def supportsStreamingMultipartParts: Boolean = false

  // SSE tests often fail with a ClosedSessionException, see https://github.com/line/armeria/issues/1754
  override protected def supportsSSE: Boolean = false
}
