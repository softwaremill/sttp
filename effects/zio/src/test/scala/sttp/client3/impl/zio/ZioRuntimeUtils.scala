package sttp.client3.impl.zio

import scala.concurrent.Future

import zio.{Exit, Runtime, Task, Unsafe, ZIO}

import sttp.client3.testing.ConvertToFuture

trait ZioRuntimeUtils {

  private val runtime: Runtime[Any] = Runtime.default

  val convertZioTaskToFuture: ConvertToFuture[Task] = new ConvertToFuture[Task] {
    override def toFuture[T](value: Task[T]): Future[T] = {
      Unsafe.unsafe(implicit u =>
        _root_.zio.Runtime.default.unsafe.runToFuture(value.tapError { e =>
          e.printStackTrace(); ZIO.unit
        })
      )
    }
  }

  def unsafeRunSync[T](task: Task[T]): Exit[Throwable, T] = {
    Unsafe.unsafeCompat { implicit u =>
      runtime.unsafe.run(task)
    }
  }

  def unsafeRunSyncOrThrow[T](task: Task[T]): T = {
    Unsafe.unsafeCompat { implicit u =>
      runtime.unsafe.run(task).getOrThrowFiberFailure()
    }
  }
}
