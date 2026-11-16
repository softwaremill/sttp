package sttp.client4.impl.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client4._
import sttp.client4.internal._
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.HttpTest.endpoint
import sttp.client4.testing.streaming.StreamingTest
import sttp.model.sse.ServerSentEvent
import zio._
import zio.stream._

trait ZioStreamingTest extends StreamingTest[Task, ZioStreams] with ZioTestBase {
  override val streams: ZioStreams = ZioStreams

  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): ZStream[Any, Throwable, Byte] =
    ZStream.fromChunks(chunks.map(Chunk.fromArray).toSeq*)

  override def bodyConsumer(stream: ZStream[Any, Throwable, Byte]): Task[String] =
    stream.runCollect.map(bytes => new String(bytes.toArray, Utf8))

  override def sseConsumer(stream: Stream[Throwable, Byte]): Task[List[ServerSentEvent]] =
    stream.viaFunction(ZioServerSentEvents.parse).runCollect.map(_.toList)

  override def neverEndingBodyProducer(
      chunk: Array[Byte],
      onStarted: () => Unit,
      onFinalized: () => Unit
  ): ZStream[Any, Throwable, Byte] =
    (ZStream.fromChunk(Chunk.fromArray(chunk)) ++ ZStream.execute(ZIO.succeed(onStarted())) ++ ZStream.never)
      .ensuring(ZIO.succeed(onFinalized()))

  override def failingBodyProducer(chunk: Array[Byte], error: Throwable): ZStream[Any, Throwable, Byte] =
    ZStream.fromChunk(Chunk.fromArray(chunk)) ++ ZStream.fail(error)

  "send a streamed request body when sending is uninterruptible" in {
    convertToFuture.toFuture(
      basicRequest
        .post(uri"$endpoint/streaming/echo")
        .streamBody(streams)(bodyProducer(List("hello".getBytes(Utf8))))
        .response(asStringAlways)
        // an uninterruptible effect can't be timed out, so the read timeout is the only way to end a hanging request
        .readTimeout(scala.concurrent.duration.Duration(5, "s"))
        .send(backend)
        .uninterruptible
        .map(_.body shouldBe "hello")
    )
  }
}
