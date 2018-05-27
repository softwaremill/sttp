package com.softwaremill.sttp.testing.streaming

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

trait StreamingTestExtensions[R[_], S] { self: StreamingTest[R, S] =>

  implicit override def executionContext: ExecutionContext = ExecutionContext.Implicits.global
}
