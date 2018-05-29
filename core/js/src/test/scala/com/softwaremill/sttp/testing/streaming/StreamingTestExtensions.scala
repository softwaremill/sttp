package com.softwaremill.sttp.testing.streaming

import scala.language.higherKinds

import com.softwaremill.sttp.testing.AsyncExecutionContext

trait StreamingTestExtensions[R[_], S] extends AsyncExecutionContext { self: StreamingTest[R, S] =>
}
