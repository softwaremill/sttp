package sttp.client.okhttp

import com.github.ghik.silencer.silent
import okhttp3.{WebSocket, WebSocketListener}
import sttp.client._
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.LowLevelListenerWebSocketTest

class OkHttpLowLevelSyncWebsocketTest extends LowLevelListenerWebSocketTest[Identity, WebSocket, WebSocketHandler] {
  override implicit val backend: SttpBackend[Identity, Nothing, WebSocketHandler] = OkHttpSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def createHandler(_onTextFrame: String => Unit): WebSocketHandler[WebSocket] =
    WebSocketHandler.fromListener(new WebSocketListener {
      @silent("discarded")
      override def onMessage(webSocket: WebSocket, text: String): Unit = {
        _onTextFrame(text)
      }
    })

  @silent("discarded")
  override def sendText(ws: WebSocket, t: String): Unit = ws.send(t) shouldBe true

  @silent("discarded")
  override def sendCloseFrame(ws: WebSocket): Unit = ws.close(1000, null) shouldBe true
}
