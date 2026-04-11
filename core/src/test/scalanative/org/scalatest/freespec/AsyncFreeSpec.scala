package org.scalatest.freespec

import scala.concurrent.ExecutionContext

// added only to make the tests compile, since it's used in shared tests
trait AsyncFreeSpec extends AnyFreeSpec {
  def executionContext: ExecutionContext = ExecutionContext.global
}
