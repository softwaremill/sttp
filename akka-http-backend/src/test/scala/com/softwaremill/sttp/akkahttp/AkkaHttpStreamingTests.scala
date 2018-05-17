package com.softwaremill.sttp.akkahttp

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.testing.ConvertToFuture
import com.softwaremill.sttp.testing.streaming.{StreamingTest, TestStreamingBackend}

import scala.concurrent.Future

class AkkaHttpStreamingTest extends StreamingTest[Future, Source[ByteString, Any]] {

  private implicit val actorSystem: ActorSystem = ActorSystem("sttp-test")
  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  override val testStreamingBackend: TestStreamingBackend[Future, Source[ByteString, Any]] =
    new AkkaHttpTestStreamingBackend(actorSystem)
}

class AkkaHttpTestStreamingBackend(
    actorSystem: ActorSystem
)(implicit materializer: Materializer)
    extends TestStreamingBackend[Future, Source[ByteString, Any]] {

  override implicit val backend: SttpBackend[Future, Source[ByteString, Any]] =
    AkkaHttpBackend.usingActorSystem(actorSystem)

  override implicit val convertToFuture: ConvertToFuture[Future] =
    ConvertToFuture.future

  override def bodyProducer(body: String): Source[ByteString, NotUsed] =
    Source.single(ByteString(body))

  override def bodyConsumer(stream: Source[ByteString, Any]): Future[String] =
    stream.map(_.utf8String).runReduce(_ + _)

}
