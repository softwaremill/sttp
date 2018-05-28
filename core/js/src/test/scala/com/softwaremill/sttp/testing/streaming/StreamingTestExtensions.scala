package com.softwaremill.sttp.testing.streaming

import scala.language.higherKinds

import com.softwaremill.sttp.testing.JSAsyncExecutionContext

trait StreamingTestExtensions[R[_], S] extends JSAsyncExecutionContext { self: StreamingTest[R, S] =>
}
