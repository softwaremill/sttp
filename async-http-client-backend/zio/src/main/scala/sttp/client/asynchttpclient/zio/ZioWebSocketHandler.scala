package sttp.client.asynchttpclient.zio

import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.internal.{AsyncQueue, NativeWebSocketHandler}
import sttp.client.impl.zio.TaskMonadAsyncError
import sttp.client.ws.{WebSocket, WebSocketEvent}
import zio.{DefaultRuntime, Queue, Runtime, Task, UIO}

object ZioWebSocketHandler {
  private class ZioAsyncQueue[A](queue: Queue[A], runtime: Runtime[Any]) extends AsyncQueue[Task, A] {
    override def clear(): Unit = {
      val _ = runtime.unsafeRunToFuture(queue.takeAll)
    }
    override def offer(t: A): Unit = {
      val _ = runtime.unsafeRunToFuture(queue.offer(t))
    }
    override def poll: Task[A] = {
      queue.take
    }
  }

  /**
    * Creates a new [[WebSocketHandler]] which should be used *once* to send and receive from a single websocket.
    */
  def create: UIO[WebSocketHandler[WebSocket[Task]]] = {
    Queue
      .unbounded[WebSocketEvent]
      .map(
        q => NativeWebSocketHandler(new ZioAsyncQueue[WebSocketEvent](q, new DefaultRuntime {}), TaskMonadAsyncError)
      )
  }
}
