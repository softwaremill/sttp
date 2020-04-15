package sttp.client.httpclient

import java.net.http.WebSocket
import java.net.http.WebSocket.Listener
import java.util.concurrent.CompletionStage

import com.github.ghik.silencer.silent
import sttp.client.testing.websocket.LowLevelListenerWebSocketTest

abstract class HttpClientLowLevelListenerWebSocketTest[F[_]]
    extends LowLevelListenerWebSocketTest[F, WebSocket, WebSocketHandler] {

  override def testErrorWhenEndpointIsNotWebsocket: Boolean = false

  override def createHandler(_onTextFrame: String => Unit): WebSocketHandler[WebSocket] =
    WebSocketHandler.fromListener(new Listener {
      var accumulator: String = ""
      override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[_] = {
        if (last) {
          _onTextFrame(accumulator + data.toString)
          accumulator = ""
        } else {
          accumulator += data.toString
        }
        super.onText(webSocket, data, last)
      }
    })

  @silent("discarded")
  override def sendText(ws: WebSocket, t: String): Unit = ws.sendText(t.toString, true).get()

  @silent("discarded")
  override def sendCloseFrame(ws: WebSocket): Unit = ws.sendClose(WebSocket.NORMAL_CLOSURE, "").get()
}
