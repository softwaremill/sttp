package sttp.client3.impl.cats

import cats.effect.{Blocker, ContextShift, IO, Timer}
import sttp.client3.ConvertFromFuture
import sttp.client3.testing.ConvertToFuture
import sttp.monad.MonadError

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

trait CatsTestBase {

  implicit def executionContext: ExecutionContext

  implicit lazy val monad: MonadError[IO] = new CatsMonadAsyncError[IO]
  implicit val contextShift: ContextShift[IO] = IO.contextShift(implicitly)
  implicit lazy val timer: Timer[IO] = IO.timer(implicitly)
  lazy val blocker: Blocker = Blocker.liftExecutionContext(implicitly)

  implicit val convertToFuture: ConvertToFuture[IO] = convertCatsIOToFuture

  implicit val convertFromFuture: ConvertFromFuture[IO] = new ConvertFromFuture[IO] {
    override def fromFuture[T](f: Future[T]): IO[T] = IO.fromFuture(IO(f))
  }

  def timeoutToNone[T](t: IO[T], timeoutMillis: Int): IO[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .handleErrorWith {
        case _: TimeoutException => IO(None)
        case e                   => throw e
      }
}
