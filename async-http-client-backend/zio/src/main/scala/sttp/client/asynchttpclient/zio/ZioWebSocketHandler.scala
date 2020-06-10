package sttp.client.asynchttpclient.zio

import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.internal.NativeWebSocketHandler
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.ws.internal.AsyncQueue
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.model.ws.WebSocketBufferFull
import zio.{Queue, Runtime, Task, ZIO}

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
    * Returns an effect, which creates a new [[WebSocketHandler]]. The handler should be used *once* to send and
    * receive from a single websocket.
    *
    * The handler will internally buffer incoming messages, and expose an instance of the [[WebSocket]] interface for
    * sending/receiving messages.
    *
    * @param incomingBufferCapacity Should the buffer of incoming websocket events be bounded. If yes, unreceived
    *                               events will some point cause the websocket to error and close. If no, unreceived
    *                               messages will eventually take up all available memory.
    */
  def apply(incomingBufferCapacity: Option[Int] = None): Task[WebSocketHandler[WebSocket[Task]]] = {
    def queue = incomingBufferCapacity match {
      case Some(capacity) => Queue.dropping[WebSocketEvent](capacity)
      case None           => Queue.unbounded[WebSocketEvent]
    }

    ZIO
      .runtime[Any]
      .flatMap(runtime => queue.map(q => NativeWebSocketHandler(new ZioAsyncQueue(q, runtime), new RIOMonadAsyncError[Any])))
  }
}
