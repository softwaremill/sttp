package sttp.client3.testing.streaming

import scala.language.higherKinds
import sttp.client3.testing.AsyncExecutionContext
import sttp.client3.testing.AsyncExecutionContext

trait StreamingTestExtensions[F[_], S] extends AsyncExecutionContext { self: StreamingTest[F, S] => }
