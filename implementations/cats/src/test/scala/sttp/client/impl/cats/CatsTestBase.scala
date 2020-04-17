package sttp.client.impl.cats

import cats.effect.{Blocker, ContextShift, IO, Timer}
import sttp.client.testing.ConvertToFuture

import scala.concurrent.ExecutionContext
import sttp.client.monad.MonadError

trait CatsTestBase {
  implicit def executionContext: ExecutionContext

  implicit lazy val monad: MonadError[IO] = new CatsMonadAsyncError[IO]
  implicit val contextShift: ContextShift[IO] = IO.contextShift(implicitly)
  implicit lazy val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)
  lazy val blocker: Blocker = Blocker.liftExecutionContext(implicitly)

  implicit val convertToFuture: ConvertToFuture[IO] = convertCatsIOToFuture
}
