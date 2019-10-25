package sttp.client.okhttp

import okhttp3.{WebSocket, WebSocketListener}

case class WebSocketHandler[WS_RESULT](listener: WebSocketListener, createResult: WebSocket => WS_RESULT)

object WebSocketHandler {
  def fromListener(listener: WebSocketListener): WebSocketHandler[WebSocket] =
    WebSocketHandler(listener, identity)
}
