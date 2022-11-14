package sttp.client3.httpclient.cats

import cats.MonadError
import cats.effect.std.{Dispatcher, Queue}
import cats.syntax.flatMap._
import sttp.client3.internal.ws.SimpleQueue
import sttp.ws.WebSocketBufferFull

class CatsSimpleQueue[F[_], A](queue: Queue[F, A], capacity: Option[Int], dispatcher: Dispatcher[F])(implicit
    F: MonadError[F, Throwable]
) extends SimpleQueue[F, A] {
  override def offer(t: A): Unit = {
    dispatcher.unsafeRunSync(
      queue
        .tryOffer(t)
        .flatMap[Unit] {
          case true  => F.unit
          case false => F.raiseError(new WebSocketBufferFull(capacity.getOrElse(Int.MaxValue)))
        }
    )
  }

  override def poll: F[A] = queue.take
}
