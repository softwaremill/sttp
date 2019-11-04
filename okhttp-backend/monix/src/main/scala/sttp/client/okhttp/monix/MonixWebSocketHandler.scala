package sttp.client.okhttp.monix

import monix.eval.Task
import monix.execution.Scheduler
import sttp.client.impl.monix.{MonixAsyncQueue, TaskMonadAsyncError}
import sttp.client.okhttp.WebSocketHandler
import sttp.client.okhttp.monix.internal.NativeWebSocketHandler
import sttp.client.ws.WebSocket

object MonixWebSocketHandler {
  /**
    * Creates a new [[WebSocketHandler]] which should be used *once* to send and receive from a single websocket.
    * @param incomingBufferCapacity Should the buffer of incoming websocket events be bounded. If yes, unreceived
    *                               events will some point cause the websocket to error and close. If no, unreceived
    *                               messages will take up all available memory.
    */
  def apply(incomingBufferCapacity: Option[Int] = None)(implicit s: Scheduler): WebSocketHandler[WebSocket[Task]] = {
    NativeWebSocketHandler(new MonixAsyncQueue(incomingBufferCapacity), TaskMonadAsyncError)
  }
}
