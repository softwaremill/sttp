package sttp.client.asynchttpclient.fs2

import cats.effect._
import cats.implicits._
import fs2.concurrent.InspectableQueue
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.internal.NativeWebSocketHandler
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.client.impl.fs2.Fs2AsyncQueue
import sttp.client.ws.{WebSocket, WebSocketEvent}

object Fs2WebSocketHandler {
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
  def apply[F[_]](
      incomingBufferCapacity: Option[Int] = None
  )(implicit F: ConcurrentEffect[F]): F[WebSocketHandler[WebSocket[F]]] =
    apply(incomingBufferCapacity.fold(InspectableQueue.unbounded[F, WebSocketEvent])(InspectableQueue.bounded))

  /**
    * Creates a new [[WebSocketHandler]] which should be used *once* to send and receive from a single websocket.
    *
    * The handler will internally buffer incoming messages, and expose an instance of the [[WebSocket]] interface for
    * sending/receiving messages.
    *
    * @param createQueue Effect to create an [[InspectableQueue]] which is used to buffer incoming messages.
    */
  def apply[F[_]](
      createQueue: F[InspectableQueue[F, WebSocketEvent]]
  )(implicit F: ConcurrentEffect[F]): F[WebSocketHandler[WebSocket[F]]] = {
    createQueue.flatMap { queue =>
      F.delay {
        NativeWebSocketHandler[F](
          new Fs2AsyncQueue[F, WebSocketEvent](queue),
          new CatsMonadAsyncError
        )
      }
    }
  }
}
