package sttp.client4.internal.ws

import sttp.ws.WebSocketFrame

private[client4] sealed trait WebSocketEvent
private[client4] object WebSocketEvent {
  case class Open() extends WebSocketEvent
  case class Error(t: Throwable) extends WebSocketEvent
  case class Frame(f: WebSocketFrame) extends WebSocketEvent
}
