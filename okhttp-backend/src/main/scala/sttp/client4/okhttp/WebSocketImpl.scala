package sttp.client4.okhttp

import java.util.concurrent.atomic.AtomicBoolean

import okhttp3.{Headers => OkHttpHeaders, Response => OkHttpResponse, WebSocket => OkHttpWebSocket, WebSocketListener}
import okio.ByteString
import sttp.client4.internal.ws.{SimpleQueue, WebSocketEvent}
import sttp.model.{Header, Headers}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.{WebSocket, WebSocketClosed, WebSocketException, WebSocketFrame}

import scala.collection.JavaConverters.asScalaIteratorConverter

private[okhttp] class WebSocketImpl[F[_]](
    ws: OkHttpWebSocket,
    queue: SimpleQueue[F, WebSocketEvent],
    _isOpen: AtomicBoolean,
    _headers: OkHttpHeaders
)(implicit
    val monad: MonadError[F]
) extends WebSocket[F] {

  /** After receiving a close frame, no further interactions with the web socket should happen. Subsequent invocations
    * of `receive`, as well as `send`, will fail with the [[sttp.ws.WebSocketClosed]] exception.
    */
  override def receive(): F[WebSocketFrame] =
    queue.poll.flatMap {
      case WebSocketEvent.Open() =>
        receive()
      case e @ WebSocketEvent.Error(t: Exception) =>
        queue.offer(e)
        monad.error(t)
      case WebSocketEvent.Error(t)                       => throw t
      case WebSocketEvent.Frame(f: WebSocketFrame.Close) =>
        queue.offer(WebSocketEvent.Error(WebSocketClosed(Some(f))))
        monad.unit(f)
      case WebSocketEvent.Frame(f: WebSocketFrame) =>
        monad.unit(f)
    }

  override def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit] =
    monad.flatten(monad.eval(f match {
      case WebSocketFrame.Text(payload, _, _) =>
        fromBoolean(ws.send(payload))
      case WebSocketFrame.Binary(payload, _, _) =>
        fromBoolean(ws.send(new ByteString(payload)))
      case WebSocketFrame.Close(statusCode, reasonText) =>
        val wasOpen = _isOpen.getAndSet(false)
        if (wasOpen) {
          fromBoolean(ws.close(statusCode, reasonText))
        } else {
          ().unit
        }
      case _: WebSocketFrame.Ping =>
        monad.error(new UnsupportedOperationException("Ping is handled by okhttp under the hood"))
      case _: WebSocketFrame.Pong =>
        monad.error(new UnsupportedOperationException("Pong is handled by okhttp under the hood"))
    }))

  private def fromBoolean(result: Boolean): F[Unit] =
    if (!result) {
      monad.error(new SendMessageException)
    } else {
      monad.unit(())
    }

  override lazy val upgradeHeaders: Headers = Headers(
    _headers.iterator().asScala.map(p => Header(p.getFirst, p.getSecond)).toList
  )

  override def isOpen(): F[Boolean] = monad.eval(_isOpen.get())
}

class SendMessageException
    extends WebSocketException(
      "Cannot enqueue next message. Socket is closed, closing or cancelled or this message would overflow the outgoing message buffer (16 MiB)"
    )

private[okhttp] class DelegatingWebSocketListener(
    delegate: WebSocketListener,
    onInitialOpen: (OkHttpWebSocket, OkHttpResponse) => Unit,
    onInitialError: Throwable => Unit
) extends WebSocketListener {
  private val initialised = new AtomicBoolean(false)

  override def onOpen(webSocket: OkHttpWebSocket, response: OkHttpResponse): Unit = {
    if (!initialised.getAndSet(true)) {
      onInitialOpen(webSocket, response)
    }
    delegate.onOpen(webSocket, response)
  }

  override def onFailure(webSocket: OkHttpWebSocket, t: Throwable, response: OkHttpResponse): Unit = {
    if (!initialised.getAndSet(true)) {
      onInitialError(t)
    }
    delegate.onFailure(webSocket, t, response)
  }

  override def onClosed(webSocket: OkHttpWebSocket, code: Int, reason: String): Unit =
    delegate.onClosed(webSocket, code, reason)
  override def onClosing(webSocket: OkHttpWebSocket, code: Int, reason: String): Unit =
    delegate.onClosing(webSocket, code, reason)
  override def onMessage(webSocket: OkHttpWebSocket, text: String): Unit = delegate.onMessage(webSocket, text)
  override def onMessage(webSocket: OkHttpWebSocket, bytes: ByteString): Unit = delegate.onMessage(webSocket, bytes)
}

private[okhttp] class AddToQueueListener[F[_]](queue: SimpleQueue[F, WebSocketEvent], isOpen: AtomicBoolean)
    extends WebSocketListener {
  override def onOpen(websocket: OkHttpWebSocket, response: OkHttpResponse): Unit = {
    isOpen.set(true)
    queue.offer(WebSocketEvent.Open())
  }

  override def onClosed(webSocket: OkHttpWebSocket, code: Int, reason: String): Unit = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Frame(WebSocketFrame.Close(code, reason)))
  }

  override def onClosing(webSocket: OkHttpWebSocket, code: Int, reason: String): Unit = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Frame(WebSocketFrame.Close(code, reason)))
  }

  override def onFailure(webSocket: OkHttpWebSocket, t: Throwable, response: OkHttpResponse): Unit = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Error(t))
  }

  override def onMessage(webSocket: OkHttpWebSocket, bytes: ByteString): Unit =
    onFrame(WebSocketFrame.Binary(bytes.toByteArray, finalFragment = true, None))
  override def onMessage(webSocket: OkHttpWebSocket, text: String): Unit =
    onFrame(WebSocketFrame.Text(text, finalFragment = true, None))

  private def onFrame(f: WebSocketFrame): Unit = queue.offer(WebSocketEvent.Frame(f))
}
