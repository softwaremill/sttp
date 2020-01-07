package sttp.client.asynchttpclient.monix

import monix.eval.Task
import monix.execution.Scheduler
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.internal.NativeWebSocketHandler
import sttp.client.impl.monix.{MonixAsyncQueue, TaskMonadAsyncError}
import sttp.client.ws.WebSocket

object MonixWebSocketHandler {

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
  def apply(
      incomingBufferCapacity: Option[Int] = None
  )(implicit s: Scheduler): Task[WebSocketHandler[WebSocket[Task]]] = {
    Task(NativeWebSocketHandler(new MonixAsyncQueue(incomingBufferCapacity), TaskMonadAsyncError))
  }
}
