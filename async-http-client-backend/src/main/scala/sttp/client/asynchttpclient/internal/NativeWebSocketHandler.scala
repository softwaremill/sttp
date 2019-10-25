package sttp.client.asynchttpclient.internal

import java.util.concurrent.atomic.AtomicBoolean

import io.netty.util.concurrent.{Future, FutureListener}
import org.asynchttpclient.ws.{WebSocket => AHCWebSocket, WebSocketListener => AHCWebSocketListener}
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.monad.syntax._
import sttp.client.monad.{MonadAsyncError, MonadError}
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.model.ws.{WebSocketClosed, WebSocketFrame}
import sttp.client.ws.internal.AsyncQueue

object NativeWebSocketHandler {
  def apply[F[_]](queue: AsyncQueue[F, WebSocketEvent], monad: MonadAsyncError[F]): WebSocketHandler[WebSocket[F]] = {
    val isOpen: AtomicBoolean = new AtomicBoolean(false)
    WebSocketHandler(
      new AddToQueueListener(queue, isOpen),
      ahcWebSocketToWebSocket(_, queue, isOpen, monad)
    )
  }

  private def ahcWebSocketToWebSocket[F[_]](
      ws: AHCWebSocket,
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
      monad.flatten(monad.eval(fromNettyFuture(f match {
        case WebSocketFrame.Text(payload, finalFragment, rsv) if !isContinuation =>
          ws.sendTextFrame(payload, finalFragment, rsv.getOrElse(0))
        case WebSocketFrame.Text(payload, finalFragment, rsv) if isContinuation =>
          ws.sendContinuationFrame(payload, finalFragment, rsv.getOrElse(0))
        case WebSocketFrame.Binary(payload, finalFragment, rsv) if !isContinuation =>
          ws.sendBinaryFrame(payload, finalFragment, rsv.getOrElse(0))
        case WebSocketFrame.Binary(payload, finalFragment, rsv) if isContinuation =>
          ws.sendContinuationFrame(payload, finalFragment, rsv.getOrElse(0))
        case WebSocketFrame.Ping(payload)                 => ws.sendPingFrame(payload)
        case WebSocketFrame.Pong(payload)                 => ws.sendPongFrame(payload)
        case WebSocketFrame.Close(statusCode, reasonText) => ws.sendCloseFrame(statusCode, reasonText)
      })))

    override def isOpen: F[Boolean] = monad.eval(_isOpen.get())

    override implicit def monad: MonadError[F] = _monad

    private def fromNettyFuture(f: io.netty.util.concurrent.Future[Void]): F[Unit] = {
      _monad.async { cb =>
        val _ = f.addListener(new FutureListener[Void] {
          override def operationComplete(future: Future[Void]): Unit = {
            if (future.isSuccess) cb(Right(())) else cb(Left(future.cause()))
          }
        })
      }
    }
  }
}

class AddToQueueListener[F[_]](queue: AsyncQueue[F, WebSocketEvent], isOpen: AtomicBoolean)
    extends AHCWebSocketListener {
  override def onOpen(websocket: AHCWebSocket): Unit = {
    isOpen.set(true)
    queue.offer(WebSocketEvent.Open())
  }

  override def onClose(websocket: AHCWebSocket, code: Int, reason: String): Unit = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Close(code, reason))
  }

  override def onError(t: Throwable): Unit = {
    isOpen.set(false)
    queue.clear() // removing any pending events so that the error is read first
    queue.offer(WebSocketEvent.Error(t))
  }

  override def onBinaryFrame(payload: Array[Byte], finalFragment: Boolean, rsv: Int): Unit =
    onFrame(WebSocketFrame.Binary(payload, finalFragment, rsvToOption(rsv)))
  override def onTextFrame(payload: String, finalFragment: Boolean, rsv: Int): Unit = {
    onFrame(WebSocketFrame.Text(payload, finalFragment, rsvToOption(rsv)))
  }

  override def onPingFrame(payload: Array[Byte]): Unit = onFrame(WebSocketFrame.Ping(payload))
  override def onPongFrame(payload: Array[Byte]): Unit = onFrame(WebSocketFrame.Pong(payload))

  private def onFrame(f: WebSocketFrame.Incoming): Unit = queue.offer(WebSocketEvent.Frame(f))

  private def rsvToOption(rsv: Int): Option[Int] = if (rsv == 0) None else Some(rsv)
}
