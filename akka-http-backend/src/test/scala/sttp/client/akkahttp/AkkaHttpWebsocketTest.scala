package sttp.client.akkahttp

import java.util.concurrent.ConcurrentLinkedQueue

import akka.Done
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import sttp.client._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client.testing.HttpTest.wsEndpoint

class AkkaHttpWebsocketTest
    extends AsyncFlatSpec
    with Matchers
    with BeforeAndAfterAll
    with Eventually
    with IntegrationPatience {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val backend: SttpBackend[Future, Nothing, Flow[Message, Message, *]] = AkkaHttpBackend()

  it should "send and receive ten messages" in {
    val received = new ConcurrentLinkedQueue[String]()

    val sink: Sink[Message, Future[Done]] = collectionSink(received)

    val n = 10
    val source: Source[Message, Promise[Option[Message]]] =
      Source((1 to n).map(i => TextMessage(s"test$i"))).concatMat(Source.maybe[Message])(Keep.right)

    val flow: Flow[Message, Message, (Future[Done], Promise[Option[Message]])] =
      Flow.fromSinkAndSourceMat(sink, source)(Keep.both)

    basicRequest.get(uri"$wsEndpoint/ws/echo").openWebsocket(flow).flatMap { r =>
      eventually {
        received.asScala.toList shouldBe (1 to n).map(i => s"echo: test$i").toList
      }

      r.result._2.complete(Success(None)) // the source should now complete
      r.result._1.map(_ => succeed) // the future should be completed once the stream completes (and the ws closes)
    }
  }

  it should "receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    val sink: Sink[Message, Future[Done]] = collectionSink(received)
    val source: Source[Message, Promise[Option[Message]]] = Source.maybe[Message]

    val flow: Flow[Message, Message, Promise[Option[Message]]] =
      Flow.fromSinkAndSourceMat(sink, source)(Keep.right)

    basicRequest.get(uri"$wsEndpoint/ws/send_and_wait").openWebsocket(flow).flatMap { r =>
      eventually {
        received.asScala.toList shouldBe List("test10", "test20")
      }
      r.result.success(None) // closing
      succeed
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
        Flow[Message]
        // mapping with parallelism 1 so that messages don't get reordered
          .mapAsync(1) {
            case m: TextMessage =>
              implicit val materializer: Materializer = _materializer
              m.toStrict(1.second).map(Some(_))
            case _ => Future.successful(None)
          }
          .collect {
            case Some(TextMessage.Strict(text)) => text
          }
          .toMat(Sink.foreach(queue.add))(Keep.right)
      }
      .mapMaterializedValue(_.flatMap(identity))

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
