package sttp.client.impl

import _root_.zio._
import _root_.zio.blocking.Blocking
import sttp.client.testing.ConvertToFuture

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

package object zio {
  val runtime: Runtime[ZEnv] = Runtime.default

  type BlockingTask[A] = ZIO[Blocking, Throwable, A]

  val convertZioTaskToFuture: ConvertToFuture[Task] = new ConvertToFuture[Task] {
    override def toFuture[T](value: Task[T]): Future[T] = {
      val p = Promise[T]()

      runtime.unsafeRunSync(value) match {
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

  val convertZioBlockingTaskToFuture: ConvertToFuture[BlockingTask] = new ConvertToFuture[BlockingTask] {
    override def toFuture[T](value: BlockingTask[T]): Future[T] = {
      val p = Promise[T]()

      runtime.unsafeRunSync(value) match {
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
}
