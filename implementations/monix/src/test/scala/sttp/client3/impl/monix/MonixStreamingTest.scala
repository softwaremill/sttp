package sttp.client3.impl.monix

import monix.eval.Task
import monix.reactive.Observable
import sttp.capabilities.monix.MonixStreams
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.streaming.StreamingTest

abstract class MonixStreamingTest extends StreamingTest[Task, MonixStreams] {
  override val streams: MonixStreams = MonixStreams

  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): Observable[Array[Byte]] =
    Observable.fromIterable(chunks)

  override def bodyConsumer(stream: Observable[Array[Byte]]): Task[String] =
    stream.toListL
      .map(bs => new String(bs.toArray.flatten, "utf8"))
}
