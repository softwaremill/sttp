package sttp.client4.testing

import org.scalatest.AsyncTestSuite

import scala.concurrent.ExecutionContext

/** The default ScalaTest async execution context runs tasks on the test thread, which causes async tests to fail with:
  * Queue is empty while future is not completed.
  */
trait AsyncExecutionContext { self: AsyncTestSuite =>
  implicit override def executionContext: ExecutionContext = ExecutionContext.global
}
