package sttp.client.httpclient.fs2

import cats.effect.{ConcurrentEffect, Sync}
import fs2.concurrent.InspectableQueue
import sttp.client.httpclient.WebSocketHandler
import sttp.client.httpclient.internal.NativeWebSocketHandler
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.client.impl.cats.implicits._
import sttp.client.impl.fs2.Fs2AsyncQueue
import sttp.client.monad.syntax._
import sttp.client.ws.{WebSocket, WebSocketEvent}

object Fs2WebSocketHandler {

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
  def apply[F[_]: ConcurrentEffect](
      incomingBufferCapacity: Option[Int] = None
  ): F[WebSocketHandler[WebSocket[F]]] =
    apply(incomingBufferCapacity.fold(InspectableQueue.unbounded[F, WebSocketEvent])(InspectableQueue.bounded))

  /**
    * Returns an effect, which creates a new [[WebSocketHandler]]. The handler should be used *once* to send and
    * receive from a single websocket.
    *
    * The handler will internally buffer incoming messages, and expose an instance of the [[WebSocket]] interface for
    * sending/receiving messages.
    *
    * @param createQueue Effect to create an [[InspectableQueue]] which is used to buffer incoming messages.
    */
  def apply[F[_]: ConcurrentEffect](
      createQueue: F[InspectableQueue[F, WebSocketEvent]]
  ): F[WebSocketHandler[WebSocket[F]]] =
    createQueue.flatMap { queue =>
      Sync[F].delay {
        NativeWebSocketHandler[F](
          new Fs2AsyncQueue[F, WebSocketEvent](queue),
          new CatsMonadAsyncError
        )
      }
    }

}
