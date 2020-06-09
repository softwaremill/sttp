package sttp.client.httpclient.zio

import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.zio._
import sttp.client.internal._
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.streaming.StreamingTest
import zio._
import zio.blocking.Blocking
import zio.stream._

class HttpClientZioStreamingTest extends StreamingTest[BlockingTask, Stream[Throwable, Byte]] {

  override implicit val backend: SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT] =
    runtime.unsafeRun(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[BlockingTask] = convertZioBlockingTaskToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): Stream[Throwable, Byte] =
    Stream.fromChunks(chunks.map(Chunk.fromArray).toSeq: _*)

  override def bodyConsumer(stream: Stream[Throwable, Byte]): Task[String] =
    stream.runCollect.map(bytes => new String(bytes.toArray, Utf8))
}
