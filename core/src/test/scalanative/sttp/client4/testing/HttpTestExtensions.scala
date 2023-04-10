package sttp.client4.testing

import scala.language.higherKinds

trait HttpTestExtensions[F[_]] extends AsyncExecutionContext
