package sttp.client4.akkahttp

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.client4.StreamBackend
import sttp.model.sse.ServerSentEvent
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.streaming.StreamingTest

import scala.concurrent.Future

class AkkaHttpStreamingTest extends StreamingTest[Future, AkkaStreams] {
  override val streams: AkkaStreams = AkkaStreams

  private implicit val actorSystem: ActorSystem = ActorSystem("sttp-test")

  override val backend: StreamBackend[Future, AkkaStreams] =
    AkkaHttpBackend.usingActorSystem(actorSystem)

  override implicit val convertToFuture: ConvertToFuture[Future] =
    ConvertToFuture.future

  override def bodyProducer(chunks: Iterable[Array[Byte]]): Source[ByteString, Any] =
    Source.apply(chunks.toList.map(ByteString(_)))

  override def bodyConsumer(stream: Source[ByteString, Any]): Future[String] =
    stream.map(_.utf8String).runReduce(_ + _)

  override def sseConsumer(stream: Source[ByteString, Any]): Future[List[ServerSentEvent]] =
    stream.via(AkkaHttpServerSentEvents.parse).runFold(List.empty[ServerSentEvent]) { case (list, event) =>
      list :+ event
    }
}
