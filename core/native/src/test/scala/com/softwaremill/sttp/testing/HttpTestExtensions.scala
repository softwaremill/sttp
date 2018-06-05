package com.softwaremill.sttp.testing

import scala.language.higherKinds

trait HttpTestExtensions[R[_]] extends AsyncExecutionContext {
  self: HttpTest[R] =>
}
