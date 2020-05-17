package sttp.client.ws

import sttp.model.ws.WebSocketFrame

sealed trait WebSocketEvent
object WebSocketEvent {
  case class Open() extends WebSocketEvent
  case class Close(code: Int, reason: String) extends WebSocketEvent
  case class Error(t: Throwable) extends WebSocketEvent
  case class Frame(f: WebSocketFrame.Incoming) extends WebSocketEvent
}
