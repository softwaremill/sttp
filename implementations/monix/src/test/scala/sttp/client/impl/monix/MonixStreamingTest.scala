package sttp.client.impl.monix

import java.nio.ByteBuffer

import monix.eval.Task
import monix.reactive.Observable
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.streaming.StreamingTest

abstract class MonixStreamingTest extends StreamingTest[Task, Observable[ByteBuffer]] {

  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): Observable[ByteBuffer] =
    Observable
      .fromIterable(chunks)
      .map(ByteBuffer.wrap)

  override def bodyConsumer(stream: Observable[ByteBuffer]): Task[String] =
    stream
      .flatMap(v => Observable.fromIterable(v.array()))
      .toListL
      .map(bs => new String(bs.toArray, "utf8"))
}
