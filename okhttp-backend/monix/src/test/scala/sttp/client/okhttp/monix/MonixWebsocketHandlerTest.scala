package sttp.client.okhttp.monix

import java.io.IOException

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.scalatest.Assertion
import sttp.client._
import sttp.client.impl.monix.{TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.okhttp.WebSocketHandler
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.WebsocketHandlerTest
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame
import scala.concurrent.duration._

class MonixWebsocketHandlerTest extends WebsocketHandlerTest[Task, WebSocketHandler] {
  override implicit val backend: SttpBackend[Task, Nothing, WebSocketHandler] =
    OkHttpMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  override implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def createHandler: Option[Int] => WebSocketHandler[WebSocket[Task]] = MonixWebSocketHandler(_)

  it should "error if the endpoint is not a websocket" in {
    monad
      .handleError {
        basicRequest
          .get(uri"$wsEndpoint/echo")
          .openWebsocket(createHandler(None))
          .map(_ => fail: Assertion)
      } {
        case e: Exception => (e shouldBe a[IOException]).unit
      }
      .toFuture()
  }

  it should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(createHandler(Some(3)))
      .flatMap { response =>
        val ws = response.result
        send(ws, 1000) >> eventually(ws.isOpen.map(_ shouldBe false))
      }
      .toFuture()
  }

  private def eventually[T](f: => Task[T]) = {
    (Task.sleep(10 millis) >> f).onErrorRestart(100)
  }

  def receiveEcho(ws: WebSocket[Task], count: Int): Task[Assertion] = {
    val fs = (1 to count).map(i => ws.receive.map(_ shouldBe Right(WebSocketFrame.text(s"echo: test$i"))))
    fs.foldLeft(Task.now(succeed))(_ >> _)
  }
}
