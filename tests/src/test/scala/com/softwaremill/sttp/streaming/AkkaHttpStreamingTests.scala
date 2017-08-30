package com.softwaremill.sttp.streaming

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.akkahttp.AkkaHttpHandler
import com.softwaremill.sttp.{ForceWrappedValue, SttpHandler}

import scala.concurrent.Future

class AkkaHttpStreamingTests(actorSystem: ActorSystem)(
    implicit materializer: Materializer)
    extends TestStreamingHandler[Future, Source[ByteString, Any]] {

  override implicit val handler: SttpHandler[Future, Source[ByteString, Any]] =
    AkkaHttpHandler.usingActorSystem(actorSystem)

  override implicit val forceResponse: ForceWrappedValue[Future] =
    ForceWrappedValue.future

  override def bodyProducer(body: String): Source[ByteString, NotUsed] =
    Source.single(ByteString(body))

  override def bodyConsumer(stream: Source[ByteString, Any]): Future[String] =
    stream.map(_.utf8String).runReduce(_ + _)

}
