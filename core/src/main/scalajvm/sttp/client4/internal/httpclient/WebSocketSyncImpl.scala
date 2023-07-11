package sttp.client4.internal.httpclient

import sttp.client4.internal._
import sttp.client4.internal.ws.{SimpleQueue, WebSocketEvent}
import sttp.model.Headers
import sttp.monad.syntax._
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}

import java.net.http.{WebSocket => JWebSocket}
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CompletableFuture
import scala.util.{Failure, Success, Try}

private[client4] class WebSocketSyncImpl[F[_]](
    ws: JWebSocket,
    queue: SimpleQueue[F, WebSocketEvent],
    _isOpen: AtomicBoolean,
    _monad: MonadError[F]
) extends WebSocket[F] {
  override def receive(): F[WebSocketFrame] =
    queue.poll.flatMap {
      case WebSocketEvent.Open() => receive()
      case WebSocketEvent.Frame(c: WebSocketFrame.Close) =>
        queue.offer(WebSocketEvent.Error(WebSocketClosed(Some(c))))
        monad.unit(c)
      case e @ WebSocketEvent.Error(t: Exception) =>
        // putting back the error so that subsequent invocations end in an error as well, instead of hanging
        queue.offer(e)
        monad.error(t)
      case WebSocketEvent.Error(t) => throw t
      case WebSocketEvent.Frame(f: WebSocketFrame) =>
        monad.eval {
          ws.request(1)
          f
        }
    }

  override def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit] =
    monad.suspend {
      f match {
        case WebSocketFrame.Text(payload, finalFragment, _) =>
          fromCompletableFutureBlocking(ws.sendText(payload, finalFragment))
        case WebSocketFrame.Binary(payload, finalFragment, _) =>
          fromCompletableFutureBlocking(ws.sendBinary(ByteBuffer.wrap(payload), finalFragment))
        case WebSocketFrame.Ping(payload) => fromCompletableFutureBlocking(ws.sendPing(ByteBuffer.wrap(payload)))
        case WebSocketFrame.Pong(payload) => fromCompletableFutureBlocking(ws.sendPong(ByteBuffer.wrap(payload)))
        case WebSocketFrame.Close(statusCode, reasonText) =>
          val wasOpen = _isOpen.getAndSet(false)
          // making close sequentially idempotent
          if (wasOpen) fromCompletableFutureBlocking(ws.sendClose(statusCode, reasonText)) else ().unit
      }
    }

  override lazy val upgradeHeaders: Headers = Headers(Nil)

  override def isOpen(): F[Boolean] = monad.eval(_isOpen.get())

  override implicit def monad: MonadError[F] = _monad

  private def fromCompletableFutureBlocking(cf: CompletableFuture[JWebSocket]): F[Unit] =
    monad.suspend {
      Try(cf.get()) match {
        case Success(_)         => ().unit
        case Failure(exception) => monad.error(exception)
      }
    }
}
