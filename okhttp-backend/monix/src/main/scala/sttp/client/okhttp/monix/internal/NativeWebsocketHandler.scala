package sttp.client.okhttp.monix.internal

import java.util.concurrent.atomic.AtomicBoolean

import okhttp3.{Response, WebSocketListener, WebSocket => OkHttpWebSocket}
import okio.ByteString
import sttp.client.monad.syntax._
import sttp.client.monad.{MonadAsyncError, MonadError}
import sttp.client.okhttp.WebSocketHandler
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
      ws: OkHttpWebSocket,
      queue: AsyncQueue[F, WebSocketEvent],
      _isOpen: AtomicBoolean,
      _monad: MonadAsyncError[F]
  ): WebSocket[F] = new WebSocket[F] {
    override def receive: F[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]] = {
      queue.poll
        .flatMap {
          case WebSocketEvent.Open() => receive
          case c: WebSocketEvent.Close =>
            queue.offer(WebSocketEvent.Error(new WebSocketClosed))
            monad.unit(Left(c))
          case e @ WebSocketEvent.Error(t: Exception) =>
            // putting back the error so that subsequent invocations end in an error as well, instead of hanging
            queue.offer(e)
            monad.error(t)
          case WebSocketEvent.Error(t) =>
            throw t
          case WebSocketEvent.Frame(f) =>
            monad.unit(Right(f))
        }
    }

    override def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit] =
      monad.flatten(monad.eval(f match {
        case WebSocketFrame.Text(payload, _, _) =>
          val bool = ws.send(payload)
          fromBoolean(bool)
        case WebSocketFrame.Binary(payload, _, _) =>
          fromBoolean(ws.send(new ByteString(payload)))
        case WebSocketFrame.Close(statusCode, reasonText) =>
          if (ws.close(statusCode, reasonText)) {
            _monad.unit(())
          } else {
            _monad.error(new WebSocketClosed)
          }
        case _: WebSocketFrame.Ping =>
          _monad.error(new UnsupportedOperationException("Ping is handled by okhttp under the hood"))
        case _: WebSocketFrame.Pong =>
          _monad.error(new UnsupportedOperationException("Pong is handled by okhttp under the hood"))
      }))

    override def isOpen: F[Boolean] = monad.eval(_isOpen.get())

    override implicit def monad: MonadError[F] = _monad

    private def fromBoolean(result: Boolean): F[Unit] = {
      if (!result) {
        _monad.error(
          new RuntimeException(
            "Cannot enqueue next message. Socket is closed, closing or " +
              "cancelled or this message would overflow the outgoing message buffer (16M MiB)"
          )
        )
      } else {
        monad.unit(())
      }
    }
  }
}

class AddToQueueListener[F[_]](queue: AsyncQueue[F, WebSocketEvent], isOpen: AtomicBoolean) extends WebSocketListener {

  override def onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String): Unit = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Close(code, reason))
  }

  override def onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String): Unit = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Close(code, reason))
  }

  override def onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response): Unit = {
    isOpen.set(false)
    queue.clear() // removing any pending events so that the error is read first
    queue.offer(WebSocketEvent.Error(t))
  }

  override def onMessage(webSocket: okhttp3.WebSocket, text: String): Unit = {
    onFrame(WebSocketFrame.Text(text, finalFragment = true, None))
  }

  override def onMessage(webSocket: okhttp3.WebSocket, bytes: ByteString): Unit = {
    onFrame(WebSocketFrame.Binary(bytes.toByteArray, finalFragment = true, None))
  }

  override def onOpen(webSocket: okhttp3.WebSocket, response: Response): Unit = {
    isOpen.set(true)
    queue.offer(WebSocketEvent.Open())
  }

  private def onFrame(f: WebSocketFrame.Incoming): Unit = queue.offer(WebSocketEvent.Frame(f))
}
