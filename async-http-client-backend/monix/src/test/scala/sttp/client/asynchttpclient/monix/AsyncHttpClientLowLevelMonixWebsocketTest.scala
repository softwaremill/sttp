package sttp.client.asynchttpclient.monix

import com.github.ghik.silencer.silent
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.asynchttpclient.ws.{WebSocketListener, WebSocket => AHCWebSocket}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.impl.monix.convertMonixTaskToFuture
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.LowLevelListenerWebSocketTest

class AsyncHttpClientLowLevelMonixWebsocketTest
    extends LowLevelListenerWebSocketTest[Task, AHCWebSocket, WebSocketHandler] {
  override implicit val backend: SttpBackend[Task, Nothing, WebSocketHandler] =
    AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def createHandler(_onTextFrame: String => Unit): WebSocketHandler[AHCWebSocket] =
    WebSocketHandler.fromListener(new WebSocketListener {
      override def onOpen(websocket: AHCWebSocket): Unit = {}
      override def onClose(websocket: AHCWebSocket, code: Int, reason: String): Unit = {}
      override def onError(t: Throwable): Unit = {}
      @silent("discarded")
      override def onTextFrame(payload: String, finalFragment: Boolean, rsv: Int): Unit = {
        _onTextFrame(payload)
      }
    })

  @silent("discarded")
  override def sendText(ws: AHCWebSocket, t: String): Unit = ws.sendTextFrame(t).await()

  @silent("discarded")
  override def sendCloseFrame(ws: AHCWebSocket): Unit = ws.sendCloseFrame()
}
