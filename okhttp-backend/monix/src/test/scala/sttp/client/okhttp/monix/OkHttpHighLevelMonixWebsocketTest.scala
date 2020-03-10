package sttp.client.okhttp.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.scalatest.Assertion
import sttp.client._
import sttp.client.impl.monix.{TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.okhttp.WebSocketHandler
import sttp.client.okhttp.monix.internal.SendMessageException
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.HighLevelWebsocketTest
import sttp.client.ws.WebSocket

import scala.concurrent.duration._

class OkHttpHighLevelMonixWebsocketTest extends HighLevelWebsocketTest[Task, WebSocketHandler] {
  override implicit val backend: SttpBackend[Task, Nothing, WebSocketHandler] =
    OkHttpMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  override implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def createHandler: Option[Int] => Task[WebSocketHandler[WebSocket[Task]]] = MonixWebSocketHandler(_)

  it should "error if the endpoint is not a websocket" in {
    monad
      .handleError {
        basicRequest
          .get(uri"$wsEndpoint/echo")
          .openWebsocketF(createHandler(None))
          .map(_ => fail: Assertion)
      } {
        case e: Exception => (e shouldBe a[SttpClientException.ReadException]).unit
      }
      .toFuture()
  }

  it should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocketF(createHandler(Some(3)))
      .flatMap { response =>
        val ws = response.result
        send(ws, 1000) >> eventually(10 millis, 400)(ws.isOpen.map(_ shouldBe false))
      }
      .onErrorRecover {
        case _: SendMessageException => succeed
      }
      .toFuture()
  }

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] = {
    (Task.sleep(interval) >> f).onErrorRestart(attempts)
  }
}
