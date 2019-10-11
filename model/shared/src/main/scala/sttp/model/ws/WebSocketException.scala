package sttp.model.ws

class WebSocketError(t: Throwable) extends Exception

class WebSocketClosed() extends Exception

class WebSocketBufferFull() extends Exception
