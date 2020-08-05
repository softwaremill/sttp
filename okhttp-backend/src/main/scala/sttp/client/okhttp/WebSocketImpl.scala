package sttp.client.okhttp

import java.util.concurrent.atomic.AtomicBoolean

import okhttp3.{WebSocket => OkHttpWebSocket}
import okio.ByteString
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocket
import sttp.client.ws.internal.{AsyncQueue, WebSocketEvent}
import sttp.model.ws.{WebSocketClosed, WebSocketException, WebSocketFrame}
import sttp.client.monad.syntax._

class WebSocketImpl[F[_]](ws:OkHttpWebSocket, queue:AsyncQueue[F,WebSocketEvent], _isOpen:AtomicBoolean)(implicit monad: MonadError[F]) extends WebSocket[F] {
  /**
    * After receiving a close frame, no further interactions with the web socket should happen. Subsequent invocations
    * of `receive`, as well as `send`, will fail with the [[sttp.model.ws.WebSocketClosed]] exception.
    */
  override def receive: F[Either[WebSocketFrame.Close, WebSocketFrame.Incoming]] = {
    queue.poll.flatMap {
      case WebSocketEvent.Open() => 
        receive
      case e @ WebSocketEvent.Error(t) =>
        queue.offer(e)
        monad.error(t)
      case WebSocketEvent.Frame(f: WebSocketFrame.Incoming) => 
        monad.unit(Right(f))
      case WebSocketEvent.Frame(f: WebSocketFrame.Close) => 
        queue.offer(WebSocketEvent.Error(new WebSocketClosed))
        monad.unit(Left(close))
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
        if (ws.close(statusCode, reasonText)) { // TODO: should be like sequentially idempotent? (like in ahc)
          monad.unit(())
        } else {
          monad.error(new WebSocketClosed)
        }
      case _: WebSocketFrame.Ping =>
        monad.error(new UnsupportedOperationException("Ping is handled by okhttp under the hood"))
      case _: WebSocketFrame.Pong =>
        monad.error(new UnsupportedOperationException("Pong is handled by okhttp under the hood"))
    }))


  private def fromBoolean(result: Boolean): F[Unit] = {
    if (!result) {
      monad.error(new SendMessageException)
    } else {
      monad.unit(())
    }
  }
  
  override def isOpen: F[Boolean] = monad.eval(_isOpen.get())
}

class SendMessageException
  extends Exception(
    "Cannot enqueue next message. Socket is closed, closing or cancelled or this message would overflow the outgoing message buffer (16 MiB)"
  )
    with WebSocketException
