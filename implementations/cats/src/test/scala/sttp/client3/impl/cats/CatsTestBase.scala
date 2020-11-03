package sttp.client3.impl.cats

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import sttp.client3.testing.ConvertToFuture

import scala.concurrent.ExecutionContext
import sttp.monad.MonadError

trait CatsTestBase {
  implicit def executionContext: ExecutionContext

  implicit lazy val monad: MonadError[IO] = new CatsMonadAsyncError[IO]
  implicit val ioRuntime: IORuntime = IORuntime.global

  implicit val convertToFuture: ConvertToFuture[IO] = convertCatsIOToFuture()
}
