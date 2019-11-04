package sttp.client.httpclient

import java.net.http.WebSocket
import java.net.http.WebSocket.Listener
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

private[httpclient] class DelegatingWebSocketListener[WS_RESULT](
    delegate: Listener,
    onInitialOpen: WebSocket => Unit,
    onInitialError: Throwable => Unit
) extends Listener {
  private val initialised = new AtomicBoolean(false)

  override def onOpen(webSocket: WebSocket): Unit = {
    if (!initialised.getAndSet(true)) {
      onInitialOpen(webSocket)
    }
    delegate.onOpen(webSocket)
  }

  override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[_] = {
    delegate.onText(webSocket, data, last)
  }

  override def onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage[_] = {
    delegate.onBinary(webSocket, data, last)
  }

  override def onPing(webSocket: WebSocket, message: ByteBuffer): CompletionStage[_] = {
    delegate.onPing(webSocket, message)
  }

  override def onPong(webSocket: WebSocket, message: ByteBuffer): CompletionStage[_] = {
    delegate.onPong(webSocket, message)
  }

  override def onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage[_] = {
    delegate.onClose(webSocket, statusCode, reason)
  }
  override def onError(webSocket: WebSocket, error: Throwable): Unit = {
    if (!initialised.getAndSet(true)) {
      onInitialError(error)
    }
    delegate.onError(webSocket, error)
  }
}
