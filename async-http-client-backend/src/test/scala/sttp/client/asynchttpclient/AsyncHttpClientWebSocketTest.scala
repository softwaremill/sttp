package sttp.client.asynchttpclient

import sttp.client.testing.websocket.{WebSocketBufferOverflowTest, WebSocketStreamingTest, WebSocketTest}

abstract class AsyncHttpClientWebSocketTest[F[_], S]
    extends WebSocketTest[F]
    with WebSocketBufferOverflowTest[F]
    with WebSocketStreamingTest[F, S] {

  override def throwsWhenNotAWebSocket: Boolean = true

  override def bufferCapacity: Int = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity.get
}
