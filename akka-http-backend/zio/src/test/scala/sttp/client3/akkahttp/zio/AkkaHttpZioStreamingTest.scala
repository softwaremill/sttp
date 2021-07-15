package sttp.client3.akkahttp.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.client3.impl.zio.{ZioServerSentEvents, ZioTestBase}
import sttp.client3.internal._
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.streaming.StreamingTest
import sttp.model.sse.ServerSentEvent
import zio.stream.Stream
import zio.{Chunk, Task}

//class AkkaHttpClientZioStreamingTest extends StreamingTest[Task, ZioStreams] with ZioTestBase {
//  override val streams: ZioStreams = ZioStreams
//  val underlying = AkkaHttpBackend()
//  override val backend: SttpBackend[Task, ZioStreams] =
//    runtime.unsafeRun(AkkaHttpClientZioBackend(underlying))
//  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture
//
//  override def bodyProducer(arrays: Iterable[Array[Byte]]): Stream[Throwable, Byte] =
//    Stream.fromChunks(arrays.map(Chunk.fromArray).toSeq: _*)
//
//  override def bodyConsumer(stream: Stream[Throwable, Byte]): Task[String] =
//    stream.runCollect.map(bytes => new String(bytes.toArray, Utf8))
//
//  override def sseConsumer(stream: Stream[Throwable, Byte]): Task[List[ServerSentEvent]] =
//    stream.via(ZioServerSentEvents.parse).runCollect.map(_.toList)
//
//  override protected def supportsStreamingMultipartParts: Boolean = false
//}
