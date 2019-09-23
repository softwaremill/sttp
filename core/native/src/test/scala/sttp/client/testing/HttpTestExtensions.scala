package sttp.client.testing

import scala.language.higherKinds

trait HttpTestExtensions[R[_]] extends AsyncExecutionContext
