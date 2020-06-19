package sttp.client.httpclient.monix

import monix.catnap.MVar
import monix.eval.Task
import monix.execution.Scheduler
import sttp.client.httpclient.WebSocketHandler
import sttp.client.httpclient.internal.NativeWebSocketHandler
import sttp.client.impl.monix.TaskMonadAsyncError
import sttp.client.ws.internal.AsyncQueue
import sttp.client.ws.{WebSocket, WebSocketEvent}

object MonixWebSocketHandler {

  /**
    * Returns an effect, which creates a new [[WebSocketHandler]]. The handler should be used *once* to send and
    * receive from a single websocket.
    */
  def apply()(implicit
      s: Scheduler
  ): Task[WebSocketHandler[WebSocket[Task]]] = {
    Task {
      val queue = new SingleElementQueue[WebSocketEvent]
      NativeWebSocketHandler(queue, TaskMonadAsyncError)
    }
  }
}

private class SingleElementQueue[A](implicit s: Scheduler) extends AsyncQueue[Task, A] {
  private val mvar = MVar.empty[Task, A]().runSyncUnsafe()

  override def offer(t: A): Unit =
    mvar.put(t).runSyncUnsafe()

  override def poll: Task[A] =
    mvar.take
}
