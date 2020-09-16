package sttp.client3.testing

import scala.concurrent.ExecutionContext

trait AsyncExecutionContext {
  implicit def executionContext: ExecutionContext = ExecutionContext.global
}
