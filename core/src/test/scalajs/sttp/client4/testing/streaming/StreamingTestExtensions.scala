package sttp.client4.testing.streaming

import scala.language.higherKinds
import sttp.client4.testing.AsyncExecutionContext

trait StreamingTestExtensions[F[_], S] extends AsyncExecutionContext { self: StreamingTest[F, S] => }
