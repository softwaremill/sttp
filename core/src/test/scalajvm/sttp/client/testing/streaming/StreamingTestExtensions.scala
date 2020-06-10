package sttp.client.testing.streaming

import scala.language.higherKinds

trait StreamingTestExtensions[F[_], S] { self: StreamingTest[F, S] => }
