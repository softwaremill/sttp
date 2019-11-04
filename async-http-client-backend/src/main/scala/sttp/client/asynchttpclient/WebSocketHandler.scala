package sttp.client.asynchttpclient

import org.asynchttpclient.ws.{WebSocket => AHCWebSocket, WebSocketListener => AHCWebSocketListener}

case class WebSocketHandler[WS_RESULT](listener: AHCWebSocketListener, createResult: AHCWebSocket => WS_RESULT)

object WebSocketHandler {
  /**
    * Create a handler from an [[AHCWebSocketListener]]. Backends can also provide higher-level websocket abstractions,
    * by providing other implementations of the [[WebSocketHandler]].
    */
  def fromListener(listener: AHCWebSocketListener): WebSocketHandler[AHCWebSocket] =
    WebSocketHandler(listener, identity)
}
