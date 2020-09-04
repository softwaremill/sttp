package sttp.client.httpclient.zio

import sttp.client.httpclient.WebSocketHandler
import sttp.client.httpclient.internal.NativeWebSocketHandler
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.client.ws.internal.AsyncQueue
import zio.{Queue, Runtime, Task, ZIO}

object ZioWebSocketHandler {
  private class ZioAsyncQueue[A](queue: Queue[A], runtime: Runtime[Any]) extends AsyncQueue[BlockingTask, A] {
    override def offer(t: A): Unit = {
      if (!runtime.unsafeRun(queue.offer(t))) {
        throw new IllegalStateException("Error while placing item in the queue")
      }
    }
    override def poll: BlockingTask[A] = {
      queue.take
    }
  }

  /**
    * Returns an effect, which creates a new [[WebSocketHandler]]. The handler should be used *once* to send and
    * receive from a single websocket.
    *
    * The handler will expose an instance of the [[WebSocket]] interface for sending/receiving messages.
    */
  def apply(): Task[WebSocketHandler[WebSocket[BlockingTask]]] = {
    def queue = Queue.bounded[WebSocketEvent](1)

    ZIO
      .runtime[Any]
      .flatMap(runtime => queue.map(q => NativeWebSocketHandler(new ZioAsyncQueue(q, runtime), new RIOMonadAsyncError)))
  }
}
