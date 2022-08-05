package sttp.client3.impl.zio

import sttp.client3.testing.ConvertToFuture
import zio.clock.Clock
import zio.duration.durationInt
import zio.{Exit, Runtime, Task, ZEnv}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

trait ZioTestBase extends ZioRuntimeUtils {

  def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.timeout(timeoutMillis.milliseconds).provideLayer(Clock.live)
}
