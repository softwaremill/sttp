package sttp.client.testing

import scala.language.higherKinds

trait HttpTestExtensions[F[_]] extends AsyncExecutionContext
