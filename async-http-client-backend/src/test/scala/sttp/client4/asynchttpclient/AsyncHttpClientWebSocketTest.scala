package sttp.client4.asynchttpclient

import sttp.client4.testing.websocket.{WebSocketBufferOverflowTest, WebSocketConcurrentTest, WebSocketTest}

abstract class AsyncHttpClientWebSocketTest[F[_]]
    extends WebSocketTest[F]
    with WebSocketBufferOverflowTest[F]
    with WebSocketConcurrentTest[F] {

  override def throwsWhenNotAWebSocket: Boolean = true

  override def bufferCapacity: Int = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity.get
}
