package sttp.client.testing.streaming

import scala.language.higherKinds
import sttp.client.testing.AsyncExecutionContext
import sttp.client.testing.AsyncExecutionContext

trait StreamingTestExtensions[R[_], S] extends AsyncExecutionContext { self: StreamingTest[R, S] =>
}
