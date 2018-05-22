package com.softwaremill.sttp.testing.streaming

import scala.language.higherKinds

trait StreamingTestExtensions[R[_], S] { self: StreamingTest[R, S] =>
}
