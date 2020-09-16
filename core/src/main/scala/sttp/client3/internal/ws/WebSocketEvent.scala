package sttp.client3.internal.ws

import sttp.ws.WebSocketFrame

sealed trait WebSocketEvent
object WebSocketEvent {
  case class Open() extends WebSocketEvent
  case class Error(t: Throwable) extends WebSocketEvent
  case class Frame(f: WebSocketFrame) extends WebSocketEvent
}
