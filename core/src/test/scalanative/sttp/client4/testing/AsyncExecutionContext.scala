package sttp.client4.testing

import scala.concurrent.ExecutionContext

trait AsyncExecutionContext {
  implicit def executionContext: ExecutionContext = ExecutionContext.global
}
