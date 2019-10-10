package sttp.client.asynchttpclient

import org.asynchttpclient.ws.{WebSocket, WebSocketListener}

case class WebSocketHandler[WR](listener: WebSocketListener)(implicit val wrIsWebSocket: WebSocket =:= WR)
