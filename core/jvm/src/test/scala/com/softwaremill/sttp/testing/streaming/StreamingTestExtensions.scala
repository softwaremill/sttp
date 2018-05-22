package com.softwaremill.sttp.testing.streaming

import scala.language.higherKinds

import com.softwaremill.sttp.testing.TestHttpServer

trait StreamingTestExtensions[R[_], S] extends TestHttpServer { self: StreamingTest[R, S] =>
}
