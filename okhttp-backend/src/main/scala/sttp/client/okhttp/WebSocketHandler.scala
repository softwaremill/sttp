package sttp.client.okhttp

import okhttp3.{WebSocket, WebSocketListener}

case class WebSocketHandler[WR](listener: WebSocketListener)(implicit val wrIsWebSocket: WebSocket =:= WR)
