package sttp.client3.impl.zio

import sttp.client3.testing.ConvertToFuture
import zio.{Exit, Runtime, Task, Unsafe, durationInt}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

trait ZioTestBase {
  private val runtime: Runtime[Any] = Runtime.default

  val convertZioTaskToFuture: ConvertToFuture[Task] = new ConvertToFuture[Task] {
    override def toFuture[T](value: Task[T]): Future[T] = {
      val p = Promise[T]()

      unsafeRunSync(value) match {
        case Exit.Failure(c) =>
          p.complete(
            Failure(
              c.failures.headOption.orElse(c.defects.headOption).getOrElse(new RuntimeException(s"Unknown cause: $c"))
            )
          )
        case Exit.Success(v) => p.complete(Success(v))
      }

      p.future
    }
  }

  def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.timeout(timeoutMillis.milliseconds)

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