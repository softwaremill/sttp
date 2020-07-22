package sttp.client.asynchttpclient.fs2

import cats.effect.IO
import org.asynchttpclient.ws.{WebSocketListener, WebSocket => AHCWebSocket}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.impl.cats.CatsTestBase
import sttp.client.testing.websocket.LowLevelListenerWebSocketTest

class AsyncHttpClientLowLevelFs2WebsocketTest
    extends LowLevelListenerWebSocketTest[IO, AHCWebSocket, WebSocketHandler]
    with CatsTestBase {
  implicit val backend: SttpBackend[IO, Any, WebSocketHandler] = AsyncHttpClientFs2Backend[IO]().unsafeRunSync()

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
}
