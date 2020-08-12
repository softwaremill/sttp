package sttp.client.impl.fs2

import cats.effect.{Effect, IO}
import fs2.concurrent.InspectableQueue
import sttp.client.ws.internal.SimpleQueue
import sttp.model.ws.WebSocketBufferFull

class Fs2SimpleQueue[F[_], A](queue: InspectableQueue[F, A])(implicit F: Effect[F]) extends SimpleQueue[F, A] {
  override def offer(t: A): Unit = {
    F.toIO(queue.offer1(t))
      .flatMap {
        case true  => IO.unit
        case false => IO.raiseError(new WebSocketBufferFull())
      }
      .unsafeRunSync()
  }

  override def poll: F[A] = queue.dequeue1
}
