package sttp.client3

import org.scalajs.dom.{WebSocket => JSWebSocket}
import sttp.client3.internal.ws.WebSocketEvent
import sttp.model.Headers
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}

import scala.scalajs.js.typedarray.{ArrayBuffer, _}

private[client3] class WebSocketImpl[F[_]] private (
    ws: JSWebSocket,
    queue: JSSimpleQueue[F, WebSocketEvent],
    implicit val monad: MonadError[F]
) extends WebSocket[F] {

  private var _isOpen = false

  override def receive(): F[WebSocketFrame] = {

    def _receive(e: WebSocketEvent): F[WebSocketFrame] = e match {
      case WebSocketEvent.Open() =>
        _isOpen = true
        queue.poll.flatMap(_receive)
      case WebSocketEvent.Frame(c: WebSocketFrame.Close) =>
        queue.offer(WebSocketEvent.Error(WebSocketClosed(Some(c))))
        monad.unit(c)
      case e @ WebSocketEvent.Error(t: Exception) =>
        queue.offer(e)
        monad.error(t)
      case WebSocketEvent.Error(t)                 => monad.error(t)
      case WebSocketEvent.Frame(f: WebSocketFrame) => monad.unit(f)
    }

    queue.poll.flatMap(_receive)
  }

  override def send(f: WebSocketFrame, isContinuation: Boolean): F[Unit] =
    f match {
      case WebSocketFrame.Text(payload, _, _) => monad.unit(ws.send(payload))
      case WebSocketFrame.Binary(payload, _, _) =>
        val ab: ArrayBuffer = payload.toTypedArray.buffer
        monad.unit(ws.send(ab))
      case WebSocketFrame.Close(statusCode, reasonText) =>
        if (_isOpen) {
          _isOpen = false
          monad.unit(ws.close(statusCode, reasonText))
        } else ().unit
      case _: WebSocketFrame.Ping => monad.error(new UnsupportedOperationException("Ping is not supported in browsers"))
      case _: WebSocketFrame.Pong => monad.error(new UnsupportedOperationException("Pong is not supported in browsers"))
    }

  override def isOpen(): F[Boolean] = monad.eval(_isOpen)

  override lazy val upgradeHeaders: Headers = Headers(Nil)
}

object WebSocketImpl {
  def newJSCoupledWebSocket[F[_]](
      ws: JSWebSocket,
      queue: JSSimpleQueue[F, WebSocketEvent]
  )(implicit monad: MonadError[F]): sttp.ws.WebSocket[F] =
    new WebSocketImpl[F](ws, queue, monad)

  val BinaryType = "arraybuffer"
}
