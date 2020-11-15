package sttp.client3.impl.cats

import java.util.concurrent.TimeoutException

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import sttp.client3.testing.ConvertToFuture
import sttp.monad.MonadError

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

trait CatsTestBase {
  implicit def executionContext: ExecutionContext

  implicit lazy val monad: MonadError[IO] = new CatsMonadAsyncError[IO]
  implicit val ioRuntime: IORuntime = IORuntime.global

  implicit val convertToFuture: ConvertToFuture[IO] = convertCatsIOToFuture()

  def timeoutToNone[T](t: IO[T], timeoutMillis: Int): IO[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .handleErrorWith {
        case _: TimeoutException => IO(None)
        case e                   => throw e
      }
}
