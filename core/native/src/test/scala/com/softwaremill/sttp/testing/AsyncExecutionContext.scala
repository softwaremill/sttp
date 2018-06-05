package com.softwaremill.sttp.testing

import scala.concurrent.ExecutionContext

trait AsyncExecutionContext {
  implicit def executionContext: ExecutionContext = ExecutionContext.Implicits.global
}
