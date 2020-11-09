package sttp.client3.impl.monix

import java.nio.ByteBuffer

import monix.eval.Task
import monix.reactive.Observable
import sttp.capabilities.monix.MonixStreams
import sttp.client3.internal.RichByteBuffer
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.streaming.StreamingTest

abstract class MonixStreamingTest extends StreamingTest[Task, MonixStreams] {
  override val streams: MonixStreams = MonixStreams

  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): Observable[ByteBuffer] =
    Observable
      .fromIterable(chunks)
      .map(ByteBuffer.wrap)

  override def bodyConsumer(stream: Observable[ByteBuffer]): Task[String] =
    stream
      .flatMap(v => Observable.fromIterable(v.safeRead()))
      .toListL
      .map(bs => new String(bs.toArray, "utf8"))
}
