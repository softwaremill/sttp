package sttp.client.asynchttpclient.future

import org.asynchttpclient.ws.{WebSocketListener, WebSocket => AHCWebSocket}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.LowLevelListenerWebSocketTest

import scala.concurrent.Future

class AsyncHttpClientLowLevelFutureWebsocketTest
    extends LowLevelListenerWebSocketTest[Future, AHCWebSocket, WebSocketHandler] {
  implicit val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()

  override def createHandler(_onTextFrame: String => Unit): WebSocketHandler[AHCWebSocket] =
    WebSocketHandler.fromListener(new WebSocketListener {
      override def onOpen(websocket: AHCWebSocket): Unit = {}
      override def onClose(websocket: AHCWebSocket, code: Int, reason: String): Unit = {}
      override def onError(t: Throwable): Unit = {}
      override def onTextFrame(payload: String, finalFragment: Boolean, rsv: Int): Unit = {
        _onTextFrame(payload)
      }
    })

  override def sendText(ws: AHCWebSocket, t: String): Unit = ws.sendTextFrame(t).await()

  override def sendCloseFrame(ws: AHCWebSocket): Unit = ws.sendCloseFrame()

  override implicit def convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
