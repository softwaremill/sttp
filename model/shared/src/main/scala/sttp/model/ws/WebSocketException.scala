package sttp.model.ws

trait WebSocketError

class WebSocketClosed() extends Exception with WebSocketError

class WebSocketBufferFull() extends Exception with WebSocketError
