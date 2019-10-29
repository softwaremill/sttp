package sttp.client.httpclient.monix

import java.nio.ByteBuffer

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Consumer, Observable}
import org.scalatest.Assertion
import sttp.client._
import sttp.client.httpclient.WebSocketHandler
import sttp.client.impl.monix.{TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.client.monad.MonadError
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.WebsocketHandlerTest
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.model.ws.WebSocketFrame
import scala.concurrent.duration._

class MonixWebsocketHandlerTest extends WebsocketHandlerTest[Task, WebSocketHandler] {
  implicit val backend: SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler] =
    HttpClientMonixBackend().runSyncUnsafe()
  implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  implicit val monad: MonadError[Task] = TaskMonadAsyncError

  def createHandler: Option[Int] => WebSocketHandler[WebSocket[Task]] = _ => MonixWebSocketHandler(5)

  it should "handle backpressure correctly" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(createHandler(Some(3)))
      .flatMap { response =>
        val ws = response.result
        send(ws, 1000) >> eventually(10.millis, 100) { ws.isOpen.map(_ shouldBe true) }
      }
      .toFuture()
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

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] = {
    (Task.sleep(interval) >> f).onErrorRestart(attempts.toLong)
  }
}
