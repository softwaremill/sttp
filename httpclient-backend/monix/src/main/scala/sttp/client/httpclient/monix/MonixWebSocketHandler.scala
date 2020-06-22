package sttp.client.httpclient.monix

import monix.eval.Task
import monix.execution.Scheduler
import sttp.client.httpclient.WebSocketHandler
import sttp.client.httpclient.internal.NativeWebSocketHandler
import sttp.client.impl.monix.{MonixAsyncQueue, TaskMonadAsyncError}
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
      val queue = new MonixAsyncQueue[WebSocketEvent](Some(2))
      NativeWebSocketHandler(queue, TaskMonadAsyncError)
    }
  }
}
