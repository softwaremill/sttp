package sttp.client.testing.streaming

import scala.language.higherKinds
import sttp.client.testing.TestHttpServer
import sttp.client.testing.TestHttpServer

trait StreamingTestExtensions[R[_], S] extends TestHttpServer { self: StreamingTest[R, S] =>
}
