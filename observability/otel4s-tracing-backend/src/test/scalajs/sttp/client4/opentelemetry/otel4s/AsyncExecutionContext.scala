package sttp.client4.opentelemetry.otel4s

import org.scalatest.AsyncTestSuite

import scala.concurrent.ExecutionContext

trait AsyncExecutionContext { self: AsyncTestSuite =>
  override def executionContext: ExecutionContext = ExecutionContext.global
}
