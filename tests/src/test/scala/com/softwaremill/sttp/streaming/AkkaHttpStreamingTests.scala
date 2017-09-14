package com.softwaremill.sttp.streaming

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import com.softwaremill.sttp.{ForceWrappedValue, SttpBackend}

import scala.concurrent.Future

class AkkaHttpStreamingTests(actorSystem: ActorSystem)(
    implicit materializer: Materializer)
    extends TestStreamingBackend[Future, Source[ByteString, Any]] {

  override implicit val backend: SttpBackend[Future, Source[ByteString, Any]] =
    AkkaHttpBackend.usingActorSystem(actorSystem)

  override implicit val forceResponse: ForceWrappedValue[Future] =
    ForceWrappedValue.future

  override def bodyProducer(body: String): Source[ByteString, NotUsed] =
    Source.single(ByteString(body))

  override def bodyConsumer(stream: Source[ByteString, Any]): Future[String] =
    stream.map(_.utf8String).runReduce(_ + _)

}
