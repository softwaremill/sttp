package sttp.client.httpclient.monix

import java.net.http.WebSocket.Listener
import java.net.http.{WebSocket => JWebSocket}
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CompletableFuture, CompletionStage}
import java.util.function.BiConsumer

import monix.eval.Task
import monix.execution.Scheduler
import sttp.client.httpclient.WebSocketHandler
import sttp.client.impl.monix.{MonixAsyncQueue, TaskMonadAsyncError}
import sttp.client.monad.syntax._
import sttp.client.monad.{Canceler, MonadAsyncError, MonadError}
import sttp.client.ws.internal.AsyncQueue
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.model.ws.{WebSocketClosed, WebSocketFrame}

object MonixWebSocketHandler {

  /**
    * Returns an effect, which creates a new [[WebSocketHandler]]. The handler should be used *once* to send and
    * receive from a single websocket.
    *
    * @param incomingBufferCapacity Amount of messages which will be buffered on client side before backpressure kicks in
    * Default value is 73 because 73 is the 21th prime number, its mirror number is the 12th prime number,
    * who’s mirror number 21 is the product of 7*3. Furthermore, 73 is 1001001 in binary, which is a palindrome.
    */
  def apply(incomingBufferCapacity: Int = 73)(
      implicit s: Scheduler
  ): Task[WebSocketHandler[WebSocket[Task]]] = {
    require(
      incomingBufferCapacity >= 2,
      "Incoming buffer capacity has to be at least 2 (opening frame + one data frame)"
    )
    Task {
      val isOpen: AtomicBoolean = new AtomicBoolean(false)
      val queue = new MonixAsyncQueue[WebSocketEvent](Some(incomingBufferCapacity))
      WebSocketHandler(
        new AddToQueueListener(queue, isOpen, incomingBufferCapacity),
        httpClientWebSocketToWebSocket(_, queue, isOpen, TaskMonadAsyncError)
      )
    }
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
        case WebSocketEvent.Frame(f) =>
          monad.eval {
            ws.request(1)
            Right(f)
          }
      }
    }

    override def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit] =
      monad.flatten(monad.eval(fromCompletableFuture(f match {
        case WebSocketFrame.Text(payload, finalFragment, _) =>
          ws.sendText(payload, finalFragment)
        case WebSocketFrame.Binary(payload, finalFragment, _) =>
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
        Canceler(() => cf.cancel(true))
      }
    }
  }
}

private class AddToQueueListener[F[_]](
    queue: AsyncQueue[F, WebSocketEvent],
    isOpen: AtomicBoolean,
    incomingBufferCapacity: Int
) extends Listener {
  override def onOpen(webSocket: JWebSocket): Unit = {
    isOpen.set(true)
    queue.offer(WebSocketEvent.Open())
    webSocket.request(incomingBufferCapacity - 1) // one is occupied by an opening frame
  }

  override def onText(webSocket: JWebSocket, data: CharSequence, last: Boolean): CompletionStage[_] = {
    onFrame(WebSocketFrame.Text(data.toString, last, None))
    null
  }

  override def onBinary(webSocket: JWebSocket, data: ByteBuffer, last: Boolean): CompletionStage[_] = {
    onFrame(WebSocketFrame.Binary(data.array(), last, None))
    null
  }

  override def onPing(webSocket: JWebSocket, message: ByteBuffer): CompletionStage[_] = {
    onFrame(WebSocketFrame.Ping(message.array()))
    null
  }

  override def onPong(webSocket: JWebSocket, message: ByteBuffer): CompletionStage[_] = {
    onFrame(WebSocketFrame.Pong(message.array()))
    null
  }

  override def onClose(webSocket: JWebSocket, statusCode: Int, reason: String): CompletionStage[_] = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Close(statusCode, reason))
    super.onClose(webSocket, statusCode, reason)
  }

  override def onError(webSocket: JWebSocket, error: Throwable): Unit = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Error(error))
    super.onError(webSocket, error)
  }

  private def onFrame(f: WebSocketFrame.Incoming): Unit = queue.offer(WebSocketEvent.Frame(f))
}
