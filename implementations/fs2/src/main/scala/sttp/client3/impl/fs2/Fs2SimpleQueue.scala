package sttp.client3.impl.fs2

import cats.MonadError
import cats.effect.std.Dispatcher
import cats.syntax.flatMap._
import fs2.concurrent.InspectableQueue
import sttp.client3.internal.ws.SimpleQueue
import sttp.ws.WebSocketBufferFull

class Fs2SimpleQueue[F[_], A](queue: InspectableQueue[F, A], capacity: Option[Int])(implicit F: MonadError[F, Throwable], D: Dispatcher[F])
    extends SimpleQueue[F, A] {
  override def offer(t: A): Unit = {
    D.unsafeRunSync(
      queue.offer1(t)
        .flatMap[Unit] {
          case true => F.unit
          case false => F.raiseError(new WebSocketBufferFull(capacity.getOrElse(Int.MaxValue)))
        }
    )
  }

  override def poll: F[A] = queue.dequeue1
}
