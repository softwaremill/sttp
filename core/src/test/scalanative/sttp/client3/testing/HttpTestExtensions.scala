package sttp.client3.testing

import scala.language.higherKinds

trait HttpTestExtensions[F[_]] extends AsyncExecutionContext
