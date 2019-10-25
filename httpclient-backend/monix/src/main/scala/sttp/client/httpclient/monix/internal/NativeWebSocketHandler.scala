package sttp.client.httpclient.monix.internal

import java.net.http.WebSocket.Listener
import java.util.concurrent.atomic.AtomicBoolean
import java.net.http.{WebSocket => JWebSocket}
import java.nio.ByteBuffer
import java.util.concurrent.{CompletableFuture, CompletionStage}
import java.util.function.BiConsumer

import sttp.client.httpclient.WebSocketHandler
import sttp.client.monad.syntax._
import sttp.client.monad.{MonadAsyncError, MonadError}
import sttp.client.ws.internal.AsyncQueue
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.model.ws.{WebSocketClosed, WebSocketFrame}

object NativeWebSocketHandler {
  def apply[F[_]](queue: AsyncQueue[F, WebSocketEvent], monad: MonadAsyncError[F]): WebSocketHandler[WebSocket[F]] = {
    val isOpen: AtomicBoolean = new AtomicBoolean(false)
    WebSocketHandler(
      new AddToQueueListener(queue, isOpen),
      httpClientWebSocketToWebSocket(_, queue, isOpen, monad)
    )
  }

  private def httpClientWebSocketToWebSocket[F[_]](
      ws: JWebSocket,
      queue: AsyncQueue[F, WebSocketEvent],
      _isOpen: AtomicBoolean,
      _monad: MonadAsyncError[F]
  ): WebSocket[F] = new WebSocket[F] {
    override def receive: F[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]] = {
      queue.poll.flatMap {
        case WebSocketEvent.Open() => receive
        case c: WebSocketEvent.Close =>
          queue.offer(WebSocketEvent.Error(new WebSocketClosed))
          monad.unit(Left(c))
        case e @ WebSocketEvent.Error(t: Exception) =>
          // putting back the error so that subsequent invocations end in an error as well, instead of hanging
          queue.offer(e)
          monad.error(t)
        case WebSocketEvent.Error(t) => throw t
        case WebSocketEvent.Frame(f) => monad.unit(Right(f))
      }
    }

    override def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit] =
      monad.flatten(monad.eval(fromCompletableFuture(f match {
        case WebSocketFrame.Text(payload, finalFragment, _) if !isContinuation =>
          ws.sendText(payload, finalFragment)
        case WebSocketFrame.Text(payload, finalFragment, _) if isContinuation =>
          ws.sendText(payload, finalFragment)
        case WebSocketFrame.Binary(payload, finalFragment, _) if !isContinuation =>
          ws.sendBinary(ByteBuffer.wrap(payload), finalFragment)
        case WebSocketFrame.Binary(payload, finalFragment, _) if isContinuation =>
          ws.sendBinary(ByteBuffer.wrap(payload), finalFragment)
        case WebSocketFrame.Ping(payload)                 => ws.sendPing(ByteBuffer.wrap(payload))
        case WebSocketFrame.Pong(payload)                 => ws.sendPong(ByteBuffer.wrap(payload))
        case WebSocketFrame.Close(statusCode, reasonText) => ws.sendClose(statusCode, reasonText)
      })))

    override def isOpen: F[Boolean] = monad.eval(_isOpen.get())

    override implicit def monad: MonadError[F] = _monad

    private def fromCompletableFuture(cf: CompletableFuture[JWebSocket]): F[Unit] = {
      _monad.async { cb =>
        cf.whenComplete(new BiConsumer[JWebSocket, Throwable] {
          override def accept(t: JWebSocket, error: Throwable): Unit = {
            if (error != null) {
              cb(Left(error))
            } else {
              cb(Right(()))
            }
          }
        })
      }
    }
  }
}

class AddToQueueListener[F[_]](queue: AsyncQueue[F, WebSocketEvent], isOpen: AtomicBoolean) extends Listener {

  override def onOpen(webSocket: JWebSocket): Unit = {
    isOpen.set(true)
    queue.offer(WebSocketEvent.Open())
    super.onOpen(webSocket)
  }

  override def onText(webSocket: JWebSocket, data: CharSequence, last: Boolean): CompletionStage[_] = {
    onFrame(WebSocketFrame.Text(data.toString, last, None))
    super.onText(webSocket, data, last)
  }

  override def onBinary(webSocket: JWebSocket, data: ByteBuffer, last: Boolean): CompletionStage[_] = {
    onFrame(WebSocketFrame.Binary(data.array(), last, None))
    super.onBinary(webSocket, data, last)
  }

  override def onPing(webSocket: JWebSocket, message: ByteBuffer): CompletionStage[_] = {
    onFrame(WebSocketFrame.Ping(message.array()))
    super.onPing(webSocket, message)
  }

  override def onPong(webSocket: JWebSocket, message: ByteBuffer): CompletionStage[_] = {
    onFrame(WebSocketFrame.Pong(message.array()))
    super.onPong(webSocket, message)
  }

  override def onClose(webSocket: JWebSocket, statusCode: Int, reason: String): CompletionStage[_] = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Close(statusCode, reason))
    super.onClose(webSocket, statusCode, reason)
  }

  override def onError(webSocket: JWebSocket, error: Throwable): Unit = {
    isOpen.set(false)
    queue.clear() // removing any pending events so that the error is read first
    queue.offer(WebSocketEvent.Error(error))
    super.onError(webSocket, error)
  }

  private def onFrame(f: WebSocketFrame.Incoming): Unit = queue.offer(WebSocketEvent.Frame(f))
}
