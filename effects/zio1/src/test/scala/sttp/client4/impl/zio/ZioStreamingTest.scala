package sttp.client4.impl.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client4.internal._
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.streaming.StreamingTest
import sttp.model.sse.ServerSentEvent
import zio._
import zio.stream._

trait ZioStreamingTest extends StreamingTest[Task, ZioStreams] with ZioTestBase {
  override val streams: ZioStreams = ZioStreams

  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): ZStream[Any, Throwable, Byte] =
    Stream.fromChunks(chunks.map(Chunk.fromArray).toSeq*)

  override def bodyConsumer(stream: ZStream[Any, Throwable, Byte]): Task[String] =
    stream.runCollect.map(bytes => new String(bytes.toArray, Utf8))

  override def sseConsumer(stream: Stream[Throwable, Byte]): Task[List[ServerSentEvent]] =
    stream.via(ZioServerSentEvents.parse).runCollect.map(_.toList)

  override def neverEndingBodyProducer(
      chunk: Array[Byte],
      onStarted: () => Unit,
      onFinalized: () => Unit
  ): ZStream[Any, Throwable, Byte] =
    (ZStream.fromChunk(Chunk.fromArray(chunk)) ++ ZStream.fromEffect(UIO(onStarted())).drain ++ ZStream.never)
      .ensuring(UIO(onFinalized()))

  override def failingBodyProducer(chunk: Array[Byte], error: Throwable): ZStream[Any, Throwable, Byte] =
    ZStream.fromChunk(Chunk.fromArray(chunk)) ++ ZStream.fail(error)
}
