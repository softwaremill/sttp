package sttp.client.asynchttpclient.monix

import monix.eval.Task
import monix.execution.{Scheduler, AsyncQueue => MonixAsyncQueue}
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.internal.{AsyncQueue, NativeWebSocketHandler}
import sttp.client.impl.monix.TaskMonadAsyncError
import sttp.client.ws.{WebSocket, WebSocketEvent}

object MonixWebSocketHandler {
  private class MonixAsyncQueue[A](implicit s: Scheduler) extends AsyncQueue[Task, A] {
    private val queue = MonixAsyncQueue.unbounded[A]()

    override def clear(): Unit = queue.clear()
    override def offer(t: A): Unit = {
      val _ = queue.offer(t)
    }
    override def poll: Task[A] = Task.deferFuture(queue.poll())
  }

  /**
    * Creates a new [[WebSocketHandler]] which should be used *once* to send and receive from a single websocket.
    */
  def create(implicit s: Scheduler): WebSocketHandler[WebSocket[Task]] = {
    NativeWebSocketHandler(new MonixAsyncQueue[WebSocketEvent](), TaskMonadAsyncError)
  }
}
