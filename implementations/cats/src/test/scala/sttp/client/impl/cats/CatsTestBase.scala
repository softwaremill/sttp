package sttp.client.impl.cats

import cats.effect.{Blocker, ContextShift, IO}
import sttp.client.testing.ConvertToFuture

import scala.concurrent.ExecutionContext

trait CatsTestBase {
  implicit def executionContext: ExecutionContext
  implicit val contextShift: ContextShift[IO] = IO.contextShift(implicitly)
  lazy val blocker: Blocker = Blocker.liftExecutionContext(implicitly)

  implicit val convertToFuture: ConvertToFuture[IO] = convertCatsIOToFuture
}
