package sttp.client3.impl.zio

import scala.concurrent.Future

import sttp.client3.testing.ConvertToFuture
import zio._
import zio.clock.Clock
import zio.duration.durationInt

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

trait ZioTestBase {

  val runtime: Runtime[ZEnv] = Runtime.default

  val convertZioTaskToFuture: ConvertToFuture[Task] = new ConvertToFuture[Task] {
    override def toFuture[T](value: Task[T]): Future[T] = {
      runtime.unsafeRunToFuture(value.tapError { e =>
        e.printStackTrace(); ZIO.unit
      })
    }
  }

  def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.timeout(timeoutMillis.milliseconds).provideLayer(Clock.live)
}
