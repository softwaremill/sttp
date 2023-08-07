package sttp.client4.impl.monix

import monix.eval.Task
import monix.reactive.Observable
import sttp.capabilities.monix.MonixStreams
import sttp.model.sse.ServerSentEvent
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.streaming.StreamingTest

abstract class MonixStreamingTest extends StreamingTest[Task, MonixStreams] {
  override val streams: MonixStreams = MonixStreams

  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def bodyProducer(chunks: Iterable[Array[Byte]]): Observable[Array[Byte]] =
    Observable.fromIterable(chunks)

  override def bodyConsumer(stream: Observable[Array[Byte]]): Task[String] =
    stream.toListL
      .map(bs => new String(bs.toArray.flatten, "utf8"))

  override def sseConsumer(stream: Observable[Array[Byte]]): Task[List[ServerSentEvent]] =
    stream.transform(MonixServerSentEvents.parse).foldLeftL(List.empty[ServerSentEvent]) { case (list, event) =>
      list :+ event
    }
}
