package sttp.client.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client._
import sttp.model.Headers

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SttpBackendStubAkkaTests extends AnyFlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem()

  override protected def afterAll(): Unit = {
    Await.result(system.terminate().map(_ => ()), 5.seconds)
  }

  "backend stub" should "cycle through responses using a single sent request" in {
    // given
    implicit val backend = AkkaHttpBackend.stub
      .whenRequestMatches(_ => true)
      .thenRespondCyclic("a", "b", "c")

    // when
    def r = basicRequest.get(uri"http://example.org/a/b/c").send().futureValue

    // then
    r.body shouldBe Right("a")
    r.body shouldBe Right("b")
    r.body shouldBe Right("c")
    r.body shouldBe Right("a")
  }

  it should "use given flow as web socket handler" in {
    // This test is an example how can we test client flow.
    // We check behavior of client when connected to echo server.
    // Client responsibility was to send two messages to the server and collect received messages.
    val useHandler: Flow[Message, Message, Future[Seq[Message]]] => Future[Seq[Message]] = clientFlow => {
      val ((outQueue, clientReceivedMessages), inQueue) = Source
        .queue(1, OverflowStrategy.fail)
        .viaMat(clientFlow)(Keep.both)
        .toMat(Sink.queue())(Keep.both)
        .run()

      def echoMsg(): Future[Unit] =
        inQueue.pull().flatMap {
          case None =>
            echoMsg()
          case Some(msg) =>
            outQueue.offer(TextMessage(s"echo: " + msg.asTextMessage.getStrictText)).map(_ => ())
        }

      (for {
        _ <- outQueue.offer(TextMessage("Hi!"))
        _ <- echoMsg()
        _ <- echoMsg()
        _ = outQueue.complete()
        _ <- outQueue.watchCompletion()
      } yield ()).flatMap(_ => clientReceivedMessages)
    }

    val clientFlow: Flow[Message, Message, Future[Seq[Message]]] = {
      Flow.fromSinkAndSourceMat(
        Sink.seq[Message],
        Source((1 to 2).map(i => TextMessage(s"test$i")))
      )(Keep.left)
    }

    implicit val b = AkkaHttpBackend.stub
      .whenRequestMatches(_ => true)
      .thenHandleOpenWebSocket(Headers(List.empty), useHandler)

    val receivedMessages = basicRequest
      .get(uri"wss://echo.websocket.org")
      .openWebsocket(clientFlow)
      .flatMap(_.result)
      .futureValue.toList

    receivedMessages shouldBe List("Hi!", "echo: test1", "echo: test2").map(TextMessage(_))
  }
}
