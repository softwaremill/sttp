package sttp.client.okhttp

import okhttp3.{WebSocket, WebSocketListener}
import sttp.client._
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.LowLevelListenerWebSocketTest

class OkHttpLowLevelSyncWebsocketTest extends LowLevelListenerWebSocketTest[Identity, WebSocket, WebSocketHandler] {
  override implicit val backend: SttpBackend[Identity, Any, WebSocketHandler] = OkHttpSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def createHandler(_onTextFrame: String => Unit): WebSocketHandler[WebSocket] =
    WebSocketHandler.fromListener(new WebSocketListener {
      override def onMessage(webSocket: WebSocket, text: String): Unit = {
        _onTextFrame(text)
      }
    })

  override def sendText(ws: WebSocket, t: String): Unit = ws.send(t) shouldBe true

  override def sendCloseFrame(ws: WebSocket): Unit = ws.close(1000, null) shouldBe true
}
