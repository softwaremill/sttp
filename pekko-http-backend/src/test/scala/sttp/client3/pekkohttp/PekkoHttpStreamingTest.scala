package sttp.client3.pekkohttp

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.Source
import pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.sse.ServerSentEvent
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.streaming.StreamingTest

import scala.concurrent.Future
import sttp.client3.SttpBackend

class PekkoHttpStreamingTest extends StreamingTest[Future, PekkoStreams] {
  override val streams: PekkoStreams = PekkoStreams

  private implicit val actorSystem: ActorSystem = ActorSystem("sttp-test")

  override val backend: SttpBackend[Future, PekkoStreams] =
    PekkoHttpBackend.usingActorSystem(actorSystem)

  override implicit val convertToFuture: ConvertToFuture[Future] =
    ConvertToFuture.future

  override def bodyProducer(chunks: Iterable[Array[Byte]]): Source[ByteString, Any] =
    Source.apply(chunks.toList.map(ByteString(_)))

  override def bodyConsumer(stream: Source[ByteString, Any]): Future[String] =
    stream.map(_.utf8String).runReduce(_ + _)

  override def sseConsumer(stream: Source[ByteString, Any]): Future[List[ServerSentEvent]] =
    stream.via(PekkoHttpServerSentEvents.parse).runFold(List.empty[ServerSentEvent]) { case (list, event) =>
      list :+ event
    }
}
