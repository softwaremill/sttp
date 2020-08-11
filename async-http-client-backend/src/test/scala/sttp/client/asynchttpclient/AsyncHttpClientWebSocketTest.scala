package sttp.client.asynchttpclient

import sttp.client.testing.websocket.{WebSocketBufferOverflowTest, WebSocketStreamingTest, WebSocketTest}

abstract class AsyncHttpClientWebSocketTest[F[_], S]
    extends WebSocketTest[F]
    with WebSocketStreamingTest[F, S]
    with WebSocketBufferOverflowTest[F] {

  override def throwsWhenNotAWebSocket: Boolean = true

  override def bufferCapacity: Int = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity.get
}
