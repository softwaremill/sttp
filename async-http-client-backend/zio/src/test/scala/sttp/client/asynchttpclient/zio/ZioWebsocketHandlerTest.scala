package sttp.client.asynchttpclient.zio

import sttp.client._
import sttp.client.asynchttpclient.{WebSocketHandler, WebsocketHandlerTest}
import sttp.client.impl.zio.{TaskMonadAsyncError, convertZioIoToFuture, runtime}
import sttp.client.monad.MonadError
import sttp.client.testing.ConvertToFuture
import sttp.client.ws.WebSocket
import zio.Task

class ZioWebsocketHandlerTest extends WebsocketHandlerTest[Task] {
  override implicit val backend: SttpBackend[Task, Nothing, WebSocketHandler] =
    runtime.unsafeRun(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioIoToFuture
  override implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def createHandler: (Option[Int]) => WebSocketHandler[WebSocket[Task]] =
    bufferCapacity => runtime.unsafeRun(ZioWebSocketHandler(bufferCapacity))

  override protected def afterAll(): Unit = {
    backend.close().toFuture
    super.afterAll()
  }
}
