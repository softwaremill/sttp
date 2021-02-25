package sttp.client3.internal

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Future, Promise}
import scala.scalajs.js

private[client3] object JSTimeout {

  def apply[T](cb: => Either[Throwable, T])(timeout: FiniteDuration, tick: FiniteDuration = 10.millis): Future[T] = {
    val p = Promise[T]()

    def poll(time: FiniteDuration = 0.millis): Unit =
      cb match {
        case Right(value) => p.success(value)
        case Left(ex) =>
          if (time >= timeout) p.failure(ex)
          else js.timers.setTimeout(tick) { poll(time + tick) }
      }

    poll()
    p.future
  }
}
