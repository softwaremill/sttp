package sttp.client4.internal.httpclient

import sttp.client4.internal._
import sttp.client4.internal.ws.{SimpleQueue, WebSocketEvent}
import sttp.model.Headers
import sttp.monad.syntax._
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}

import java.net.http.WebSocket.Listener
import java.net.http.{WebSocket => JWebSocket}
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CompletableFuture, CompletionStage}

private[client4] class WebSocketImpl[F[_]](
    ws: JWebSocket,
    queue: SimpleQueue[F, WebSocketEvent],
    _isOpen: AtomicBoolean,
    sequencer: Sequencer[F],
    _monad: MonadError[F],
    fromCompletableFutureToEffect: CompletableFuture[JWebSocket] => F[Unit]
) extends WebSocket[F] {
  override def receive(): F[WebSocketFrame] =
    queue.poll.flatMap {
      case WebSocketEvent.Open()                         => receive()
      case WebSocketEvent.Frame(c: WebSocketFrame.Close) =>
        queue.offer(WebSocketEvent.Error(WebSocketClosed(Some(c))))
        monad.unit(c)
      case e @ WebSocketEvent.Error(t: Exception) =>
        // putting back the error so that subsequent invocations end in an error as well, instead of hanging
        queue.offer(e)
        monad.error(t)
      case WebSocketEvent.Error(t)                 => throw t
      case WebSocketEvent.Frame(f: WebSocketFrame) =>
        monad.eval {
          ws.request(1)
          f
        }
    }

  override def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit] =
    // ws.send* is not thread-safe - at least one can run at a time. Hence, adding a sequencer to ensure that
    // even if called concurrently, these will be run in sequence.
    sequencer(monad.suspend {
      f match {
        case WebSocketFrame.Text(payload, finalFragment, _) =>
          fromCompletableFutureToEffect(ws.sendText(payload, finalFragment))
        case WebSocketFrame.Binary(payload, finalFragment, _) =>
          fromCompletableFutureToEffect(ws.sendBinary(ByteBuffer.wrap(payload), finalFragment))
        case WebSocketFrame.Ping(payload) => fromCompletableFutureToEffect(ws.sendPing(ByteBuffer.wrap(payload)))
        case WebSocketFrame.Pong(payload) => fromCompletableFutureToEffect(ws.sendPong(ByteBuffer.wrap(payload)))
        case WebSocketFrame.Close(statusCode, reasonText) =>
          val wasOpen = _isOpen.getAndSet(false)
          // making close sequentially idempotent
          if (wasOpen) fromCompletableFutureToEffect(ws.sendClose(statusCode, reasonText)) else ().unit
      }
    })

  override lazy val upgradeHeaders: Headers = Headers(Nil)

  override implicit def monad: MonadError[F] = _monad

  override def isOpen(): F[Boolean] = monad.eval(_isOpen.get())

}

private[client4] class AddToQueueListener[F[_]](
    queue: SimpleQueue[F, WebSocketEvent],
    isOpen: AtomicBoolean
) extends Listener {
  override def onOpen(webSocket: JWebSocket): Unit = {
    isOpen.set(true)
    queue.offer(WebSocketEvent.Open())
    webSocket.request(1)
  }

  override def onText(webSocket: JWebSocket, data: CharSequence, last: Boolean): CompletionStage[_] = {
    onFrame(WebSocketFrame.Text(data.toString, last, None))
    null
  }

  override def onBinary(webSocket: JWebSocket, data: ByteBuffer, last: Boolean): CompletionStage[_] = {
    onFrame(WebSocketFrame.Binary(data.safeRead(), last, None))
    null
  }

  override def onPing(webSocket: JWebSocket, message: ByteBuffer): CompletionStage[_] = {
    onFrame(WebSocketFrame.Ping(message.safeRead()))
    null
  }

  override def onPong(webSocket: JWebSocket, message: ByteBuffer): CompletionStage[_] = {
    onFrame(WebSocketFrame.Pong(message.safeRead()))
    null
  }

  override def onClose(webSocket: JWebSocket, statusCode: Int, reason: String): CompletionStage[_] = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Frame(WebSocketFrame.Close(statusCode, reason)))
    super.onClose(webSocket, statusCode, reason)
  }

  override def onError(webSocket: JWebSocket, error: Throwable): Unit = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Error(error))
    super.onError(webSocket, error)
  }

  private def onFrame(f: WebSocketFrame): Unit = queue.offer(WebSocketEvent.Frame(f))
}
