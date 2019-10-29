package sttp.client.asynchttpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.client._
import sttp.client.asynchttpclient.{AHCWebsocketHandlerTest, WebSocketHandler}
import sttp.client.impl.monix.{TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.client.monad.MonadError
import sttp.client.testing.ConvertToFuture
import sttp.client.ws.WebSocket

class MonixWebsocketHandlerTest extends AHCWebsocketHandlerTest[Task] {
  override implicit val backend: SttpBackend[Task, Nothing, WebSocketHandler] =
    AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  override implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def createHandler: Option[Int] => WebSocketHandler[WebSocket[Task]] = MonixWebSocketHandler(_)
}
