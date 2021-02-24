package sttp.client3

import org.scalajs.dom.{window, WebSocket => JSWebSocket}
import sttp.client3.WebSocketImpl.OpenState
import sttp.client3.internal.ws.WebSocketEvent
import sttp.client3.ws.WebSocketTimeoutException
import sttp.model.Headers
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Future, Promise}
import scala.scalajs.js.typedarray.{ArrayBuffer, DataView}
import scala.util.{Failure, Success, Try}

private[client3] class WebSocketImpl[F[_]](
    ws: JSWebSocket,
    queue: JSSimpleQueue[WebSocketEvent],
    timeout: FiniteDuration,
    fromFuture: FromFuture[F],
    implicit val monad: MonadError[F]
) extends WebSocket[F] {

  override def receive(): F[WebSocketFrame] = fromFuture.apply {

    def _receive(e: WebSocketEvent): Future[WebSocketFrame] = e match {
      case WebSocketEvent.Open() => queue.poll.flatMap(_receive)
      case WebSocketEvent.Frame(c: WebSocketFrame.Close) =>
        queue.offer(WebSocketEvent.Error(WebSocketClosed(Some(c))))
        Future.successful[WebSocketFrame](c)
      case e @ WebSocketEvent.Error(t: Exception) =>
        queue.offer(e)
        Future.failed[WebSocketFrame](t)
      case WebSocketEvent.Error(t)                 => throw t
      case WebSocketEvent.Frame(f: WebSocketFrame) => Future.successful[WebSocketFrame](f)
    }

    val x = queue.poll.flatMap(_receive)

    x.flatMap(f => {
      println(s"polled f... $f")
      Future.successful(f)
    })
  }

  override def send(f: WebSocketFrame, isContinuation: Boolean): F[Unit] = fromFuture.apply {
    val p = Promise[Unit]()
    val tick = 10.millis

    def _send(time: FiniteDuration = 0.millis): Unit = {
      if (time >= timeout) p.failure(new WebSocketTimeoutException)
      else if (isInOpenState) {
        send(f) match {
          case Success(_)  => p.success(())
          case Failure(ex) => p.failure(ex)
        }
      } else window.setTimeout(() => _send(time + tick), tick.toMillis.toDouble)
    }

    _send()
    p.future
  }

  private def send(f: WebSocketFrame): Try[Unit] =
    Try {
      f match {
        case WebSocketFrame.Text(payload, _, _) =>
          println("sending text...")
          ws.send(payload)
          println("send text...")
        case WebSocketFrame.Binary(payload, _, _) =>
          val ab: ArrayBuffer = new ArrayBuffer(payload.length)
          val dv = new DataView(ab)
          (0 to payload.length) foreach { i => dv.setInt8(i, payload(i)) }
          println("sending bytes...")
          ws.send(ab)
          println("send bytes...")
        case WebSocketFrame.Close(statusCode, reasonText) =>
          println("sending close...")
          ws.close(statusCode, reasonText)
          println("closed...")
        case _: WebSocketFrame.Ping => throw new UnsupportedOperationException("Ping is not supported in browsers")
        case _: WebSocketFrame.Pong => throw new UnsupportedOperationException("Pong is not supported in browsers")
      }
    }

  override def isOpen(): F[Boolean] = monad.eval(isInOpenState)

  private def isInOpenState = ws.readyState == OpenState

  override lazy val upgradeHeaders: Headers = Headers(Nil)
}

object WebSocketImpl {
  def newJSCoupledWebSocket[F[_]](ws: JSWebSocket, queue: JSSimpleQueue[WebSocketEvent], timeout: FiniteDuration)(
      implicit
      fromFuture: FromFuture[F],
      monad: MonadError[F]
  ): sttp.ws.WebSocket[F] =
    new WebSocketImpl[F](ws, queue, timeout, fromFuture, monad)

  val OpenState = 1
  val BinaryType = "arraybuffer"
}
