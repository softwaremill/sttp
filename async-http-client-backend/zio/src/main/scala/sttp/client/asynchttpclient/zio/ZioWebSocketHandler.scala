package sttp.client.asynchttpclient.zio

import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.internal.NativeWebSocketHandler
import sttp.client.impl.zio.TaskMonadAsyncError
import sttp.client.ws.internal.AsyncQueue
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.model.ws.WebSocketBufferFull
import zio.{Queue, Runtime, Task, UIO, ZIO}

object ZioWebSocketHandler {
  private class ZioAsyncQueue[A](queue: Queue[A], runtime: Runtime[Any]) extends AsyncQueue[Task, A] {
    override def offer(t: A): Unit = {
      if (!runtime.unsafeRun(queue.offer(t))) {
        throw new WebSocketBufferFull()
      }
    }
    override def poll: Task[A] = {
      queue.take
    }
  }

  /**
    * Creates a new [[WebSocketHandler]] which should be used *once* to send and receive from a single websocket.
    *
    * The handler will internally buffer incoming messages, and expose an instance of the [[WebSocket]] interface for
    * sending/receiving messages.
    *
    * @param incomingBufferCapacity Should the buffer of incoming websocket events be bounded. If yes, unreceived
    *                               events will some point cause the websocket to error and close. If no, unreceived
    *                               messages will eventually take up all available memory.
    */
  def apply(incomingBufferCapacity: Option[Int] = None): UIO[WebSocketHandler[WebSocket[Task]]] = {
    val queue = incomingBufferCapacity match {
      case Some(capacity) => Queue.dropping[WebSocketEvent](capacity)
      case None           => Queue.unbounded[WebSocketEvent]
    }

    ZIO
      .runtime[Any]
      .flatMap(runtime => queue.map(q => NativeWebSocketHandler(new ZioAsyncQueue(q, runtime), TaskMonadAsyncError)))
  }
}
