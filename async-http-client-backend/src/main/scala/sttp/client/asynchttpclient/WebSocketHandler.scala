package sttp.client.asynchttpclient

import org.asynchttpclient.ws.{WebSocket => AHCWebSocket, WebSocketListener => AHCWebSocketListener}

case class WebSocketHandler[WS_RESULT](listener: AHCWebSocketListener, createResult: AHCWebSocket => WS_RESULT)

object WebSocketHandler {
  def fromListener(listener: AHCWebSocketListener): WebSocketHandler[AHCWebSocket] =
    WebSocketHandler(listener, identity)
}
