package sttp.client3.impl.zio

import scala.concurrent.Future

import zio.{Runtime, Task, ZEnv, ZIO}

import sttp.client3.testing.ConvertToFuture

trait ZioRuntimeUtils {

  val runtime: Runtime[ZEnv] = Runtime.default

  val convertZioTaskToFuture: ConvertToFuture[Task] = new ConvertToFuture[Task] {
    override def toFuture[T](value: Task[T]): Future[T] = {
      runtime.unsafeRunToFuture(value.tapError { e =>
        e.printStackTrace(); ZIO.unit
      })
    }
  }
}
