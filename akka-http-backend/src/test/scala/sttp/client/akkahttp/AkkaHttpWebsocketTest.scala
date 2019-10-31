package sttp.client.akkahttp

import java.util.concurrent.ConcurrentLinkedQueue

import akka.Done
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl._
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{AsyncFlatSpec, Matchers}
import sttp.client._
import sttp.client.testing.TestHttpServer

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success

class AkkaHttpWebsocketTest
    extends AsyncFlatSpec
    with Matchers
    with TestHttpServer
    with Eventually
    with IntegrationPatience {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val backend: SttpBackend[Future, Nothing, Flow[Message, Message, *]] = AkkaHttpBackend()

  it should "send and receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()

    val sink: Sink[Message, Future[Done]] = collectionSink(received)

    val source: Source[Message, Promise[Option[Message]]] =
      Source(List(TextMessage("test1"), TextMessage("test2"))).concatMat(Source.maybe[Message])(Keep.right)

    val flow: Flow[Message, Message, (Future[Done], Promise[Option[Message]])] =
      Flow.fromSinkAndSourceMat(sink, source)(Keep.both)

    basicRequest.get(uri"$wsEndpoint/ws/echo").openWebsocket(flow).flatMap { r =>
      eventually {
        received.asScala.toList shouldBe List("echo: test1", "echo: test2")
      }

      r.result._2.complete(Success(None)) // the source should now complete
      r.result._1.map(_ => succeed) // the future should be completed once the stream completes (and the ws closes)
    }
  }

  it should "receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    val sink: Sink[Message, Future[Done]] = collectionSink(received)
    val source: Source[Message, Promise[Option[Message]]] = Source.maybe[Message]

    val flow: Flow[Message, Message, (Future[Done], Promise[Option[Message]])] =
      Flow.fromSinkAndSourceMat(sink, source)(Keep.both)

    basicRequest.get(uri"$wsEndpoint/ws/send_and_close").openWebsocket(flow).flatMap { r =>
      eventually {
        received.asScala.toList shouldBe List("test10", "test20")
      }

      r.result._1
        .map(_ => succeed) // the future should be completed once the stream completes (server should close the ws)
    }
  }

  it should "error if the endpoint is not a websocket" in {
    basicRequest.get(uri"$wsEndpoint/echo").openWebsocket(Flow.apply[Message]).failed.map { t =>
      t shouldBe a[NotAWebsocketException]
    }
  }

  def collectionSink(queue: ConcurrentLinkedQueue[String]): Sink[Message, Future[Done]] =
    Sink
      .setup[Message, Future[Done]] { (_materializer, _) =>
        Sink.foreach[Message] {
          case m: TextMessage =>
            implicit val materializer: Materializer = _materializer
            m.toStrict(1.second).foreach(s => queue.add(s.text))
          case _ =>
        }
      }
      .mapMaterializedValue(_.flatMap(identity))

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
