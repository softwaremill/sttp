package sttp.client3.impl.cats

import java.util.concurrent.TimeoutException

import cats.effect.{Blocker, ContextShift, IO, Timer}
import sttp.client3.testing.ConvertToFuture

import scala.concurrent.ExecutionContext
import sttp.monad.MonadError

import scala.concurrent.duration.DurationInt

trait CatsTestBase {
  implicit def executionContext: ExecutionContext

  implicit lazy val monad: MonadError[IO] = new CatsMonadAsyncError[IO]
  implicit val contextShift: ContextShift[IO] = IO.contextShift(implicitly)
  implicit lazy val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)
  lazy val blocker: Blocker = Blocker.liftExecutionContext(implicitly)

  implicit val convertToFuture: ConvertToFuture[IO] = convertCatsIOToFuture

  def timeoutToNone[T](t: IO[T], timeoutMillis: Int): IO[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .handleErrorWith {
        case _: TimeoutException => IO(None)
        case e                   => throw e
      }
}
