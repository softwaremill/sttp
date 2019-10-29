package sttp.client.asynchttpclient.monix

import monix.eval.Task
import monix.execution.{Scheduler, AsyncQueue => MonixAsyncQueue}
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.internal.{AsyncQueue, NativeWebSocketHandler}
import sttp.client.impl.monix.TaskMonadAsyncError
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketBufferFull

object MonixWebSocketHandler {
  private class MonixAsyncQueue[A](bufferCapacity: Option[Int])(implicit s: Scheduler) extends AsyncQueue[Task, A] {
    private val queue = bufferCapacity match {
      case Some(capacity) => MonixAsyncQueue.bounded[A](capacity)
      case None           => MonixAsyncQueue.unbounded[A]()
    }

    override def clear(): Unit = queue.clear()
    override def offer(t: A): Unit = {
      if (!queue.tryOffer(t)) {
        throw new WebSocketBufferFull()
      }
    }
    override def poll: Task[A] = Task.deferFuture(queue.poll())
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
  def apply(incomingBufferCapacity: Option[Int] = None)(implicit s: Scheduler): WebSocketHandler[WebSocket[Task]] = {
    NativeWebSocketHandler(new MonixAsyncQueue(incomingBufferCapacity), TaskMonadAsyncError)
  }
}
