package sttp.client.testing.streaming

import scala.language.higherKinds
import sttp.client.testing.AsyncExecutionContext
import sttp.client.testing.AsyncExecutionContext

trait StreamingTestExtensions[F[_], S] extends AsyncExecutionContext { self: StreamingTest[F, S] => }
