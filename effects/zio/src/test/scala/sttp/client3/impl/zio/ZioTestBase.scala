package sttp.client3.impl.zio

import zio.{Task, durationInt}

trait ZioTestBase extends ZioRuntimeUtils {

  def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.timeout(timeoutMillis.milliseconds)

}
