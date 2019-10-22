package sttp.client.httpclient

import java.net.http.WebSocket
import java.net.http.WebSocket.Listener

case class WebSocketHandler[WR](listener: Listener)(implicit val wrIsWebSocket: WebSocket =:= WR)
