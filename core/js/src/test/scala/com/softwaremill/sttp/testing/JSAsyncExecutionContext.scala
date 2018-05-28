package com.softwaremill.sttp.testing

import scala.concurrent.ExecutionContext

import org.scalatest.AsyncTestSuite

/**
  * The default ScalaTest async execution context uses `scala.scalajs.concurrent.JSExecutionContext.Implicits.queue`
  * which causes async tests to failed with:
  *   Queue is empty while future is not completed, this means you're probably
  *   using a wrong ExecutionContext for your task, please double check your Future.
  */
trait JSAsyncExecutionContext { self: AsyncTestSuite =>

  implicit override def executionContext: ExecutionContext = ExecutionContext.Implicits.global
}
