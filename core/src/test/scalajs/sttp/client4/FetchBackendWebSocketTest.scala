package sttp.client4

import sttp.client4.fetch.FetchBackend
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.websocket.WebSocketTest
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class FetchBackendWebSocketTest extends WebSocketTest[Future] {

  implicit override def executionContext: ExecutionContext = queue
  override def throwsWhenNotAWebSocket: Boolean = true

  override val backend: WebSocketBackend[Future] = FetchBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override implicit def monad: MonadError[Future] = new FutureMonad()
}
