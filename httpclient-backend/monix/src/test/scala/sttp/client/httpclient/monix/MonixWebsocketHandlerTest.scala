package sttp.client.httpclient.monix

import java.nio.ByteBuffer

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Consumer, Observable}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{Assertion, AsyncFlatSpec, Matchers}
import sttp.client._
import sttp.client.httpclient.WebSocketHandler
import sttp.client.impl.monix.{TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.client.monad.MonadError
import sttp.client.testing.{ConvertToFuture, TestHttpServer, ToFutureWrapper}
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.model.ws.WebSocketFrame

class MonixWebsocketHandlerTest
    extends AsyncFlatSpec
    with Matchers
    with TestHttpServer
    with ToFutureWrapper
    with Eventually
    with IntegrationPatience {
  implicit val backend: SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler] =
    HttpClientMonixBackend().runSyncUnsafe()
  implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  implicit val monad: MonadError[Task] = TaskMonadAsyncError

  def createHandler: Option[Int] => WebSocketHandler[WebSocket[Task]] = MonixWebSocketHandler(_)

  it should "send and receive two messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(createHandler(None))
      .flatMap { response =>
        val ws = response.result
        send(ws, 2) >>
          receiveEcho(ws, 2) >>
          ws.close >>
          Task.now(succeed)
      }
      .toFuture
  }

  it should "send and receive 1000 messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(createHandler(None))
      .flatMap { response =>
        val ws = response.result
        send(ws, 1000) >>
          receiveEcho(ws, 1000) >>
          ws.close >>
          Task.now(succeed)
      }
      .toFuture
  }

  it should "receive two messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_close")
      .openWebsocket(createHandler(None))
      .flatMap { response =>
        val ws = response.result
        ws.receive.map(_ shouldBe Right(WebSocketFrame.text("test10"))) >>
          ws.receive.map(_ shouldBe Right(WebSocketFrame.text("test20"))) >>
          ws.receive.map(_ shouldBe 'left)
      }
      .toFuture
  }

  it should "error if incoming messages overflow the buffer" in {
    monad
      .handleError {
        basicRequest
          .get(uri"$wsEndpoint/ws/echo")
          .openWebsocket(createHandler(Some(3)))
          .flatMap { response =>
            val ws = response.result
            send(ws, 1000) >>
              // by now we expect to have received at least 4 back, which should overflow the buffer
              ws.isOpen.map(_ shouldBe false)
          }
      } {
        case _: Exception => Task.now(succeed)
      }
      .toFuture()
  }

  def send(ws: WebSocket[Task], count: Int): Task[Unit] = {
    val fs = (1 to count).map(i => ws.send(WebSocketFrame.text(s"test$i")))
    fs.foldLeft(Task.now(()))(_ >> _)
  }

  def receiveEcho(ws: WebSocket[Task], count: Int): Task[Assertion] = {
    val fs = (1 to count).map { i =>
      Observable
        .fromIterable(1 to Int.MaxValue)
        .mapEval(_ => ws.receive)
        .takeWhileInclusive {
          case Right(value: WebSocketFrame.Text) => !value.finalFragment
          case _                                 => false
        }
        .consumeWith(
          Consumer.foldLeft[Either[Unit, String], Either[WebSocketEvent.Close, WebSocketFrame.Incoming]](Right(""))(
            (a, b) =>
              (a, b) match {
                case (Right(acc), Right(f2: WebSocketFrame.Text)) => Right(acc + f2.payload)
                case _                                            => Left(())
              }
          )
        )
        .map(payload => payload shouldBe Right(s"echo: test$i"))
    }
    fs.foldLeft(Task.now(succeed))(_ >> _)
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture
    super.afterAll()
  }
}
