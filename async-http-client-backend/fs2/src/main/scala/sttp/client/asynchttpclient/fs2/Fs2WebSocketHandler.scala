package sttp.client.asynchttpclient.fs2

import cats.effect._
import cats.implicits._
import fs2.concurrent.InspectableQueue
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.internal.{AsyncQueue, NativeWebSocketHandler}
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.model.ws.WebSocketBufferFull

object Fs2WebSocketHandler {
  private class Fs2AsyncQueue[F[_], A](queue: InspectableQueue[F, A])(implicit F: Effect[F]) extends AsyncQueue[F, A] {
    override def clear(): Unit =
      F.runAsync(queue.getSize.flatMap { size =>
        queue.dequeue.take(size.toLong).compile.drain
      })(IO.fromEither).unsafeRunSync()

    override def offer(t: A): Unit =
      F.runAsync(queue.offer1(t))(IO.fromEither(_).flatMap {
        case true => IO.unit
        case false => IO.raiseError(new WebSocketBufferFull())
      }).unsafeRunSync()

    override def poll: F[A] = queue.dequeue1
  }
  /**
    * Creates a new [[WebSocketHandler]] which should be used *once* to send and receive from a single websocket.
    * @param incomingBufferCapacity Should the buffer of incoming websocket events be bounded. If yes, unreceived
    *                               events will some point cause the websocket to error and close. If no, unreceived
    *                               messages will take up all available memory.
    */
  def apply[F[_]](incomingBufferCapacity: Option[Int] = None)(implicit F: ConcurrentEffect[F]): F[WebSocketHandler[WebSocket[F]]] =
    apply(incomingBufferCapacity.fold(InspectableQueue.unbounded[F, WebSocketEvent])(InspectableQueue.bounded))

  /**
    * Creates a new [[WebSocketHandler]] which should be used *once* to send and receive from a single websocket.
    * @param createQueue Effect to create an [[InspectableQueue]] which is used to buffer incoming messages.
    */
  def apply[F[_]](createQueue: F[InspectableQueue[F, WebSocketEvent]])(implicit F: Effect[F]): F[WebSocketHandler[WebSocket[F]]] = {
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
