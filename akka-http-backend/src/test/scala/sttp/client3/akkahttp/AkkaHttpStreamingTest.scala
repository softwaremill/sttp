package sttp.client3.akkahttp

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.client3.SttpBackend
import sttp.client3.sse.ServerSentEvent
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.streaming.StreamingTest

import scala.concurrent.Future

class AkkaHttpStreamingTest extends StreamingTest[Future, AkkaStreams] {
  override val streams: AkkaStreams = AkkaStreams

  private implicit val actorSystem: ActorSystem = ActorSystem("sttp-test")

  override val backend: SttpBackend[Future, AkkaStreams] =
    AkkaHttpBackend.usingActorSystem(actorSystem)

  override implicit val convertToFuture: ConvertToFuture[Future] =
    ConvertToFuture.future

  override def bodyProducer(chunks: Iterable[Array[Byte]]): Source[ByteString, Any] =
    Source.apply(chunks.toList.map(ByteString(_)))

  override def bodyConsumer(stream: Source[ByteString, Any]): Future[String] =
    stream.map(_.utf8String).runReduce(_ + _)

  override def sseConsumer(stream: Source[ByteString, Any]): Future[List[ServerSentEvent]] = ???
}
