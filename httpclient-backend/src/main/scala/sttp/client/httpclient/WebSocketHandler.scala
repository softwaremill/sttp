package sttp.client.httpclient

import java.net.http.WebSocket
import java.net.http.WebSocket.Listener

case class WebSocketHandler[WS_RESULT](listener: Listener, createResult: WebSocket => WS_RESULT)

object WebSocketHandler {
  def fromListener(listener: Listener): WebSocketHandler[WebSocket] =
    WebSocketHandler(listener, identity)
}
