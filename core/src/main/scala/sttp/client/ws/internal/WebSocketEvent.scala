package sttp.client.ws.internal

import sttp.model.ws.WebSocketFrame

sealed trait WebSocketEvent
object WebSocketEvent {
  case class Open() extends WebSocketEvent
  case class Error(t: Throwable) extends WebSocketEvent
  case class Frame(f: WebSocketFrame) extends WebSocketEvent
}
