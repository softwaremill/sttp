package com.softwaremill.sttp.http4s

import cats.implicits._
import cats.effect.concurrent.MVar
import cats.effect.{ContextShift, IO, Resource}

// an ugly hack to use a Resource in an imperative way
object ExtractFromResource {
  def apply[T](r: Resource[IO, T])(implicit cf: ContextShift[IO]): (T, () => Unit) = {
    val tReady: MVar[IO, T] = MVar.empty[IO, T].unsafeRunSync()
    val done: MVar[IO, Unit] = MVar.empty[IO, Unit].unsafeRunSync()

    // first extracting it from the use method. use will only complete when the `done` mvar is filled, which is done
    // by the returned method
    r.use { _t =>
        tReady.put(_t) >> done.take
      }
      .start
      .unsafeRunSync()

    (tReady.take.unsafeRunSync(), () => done.put(()).unsafeRunSync())
  }
}
