package com.softwaremill.sttp

import scala.concurrent.Future

import com.softwaremill.sttp.testing.AbstractCurlBackendHttpTest
import com.softwaremill.sttp.testing.ConvertToFuture

class CurlBackendHttpTest extends AbstractCurlBackendHttpTest[Future, Nothing] {
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override implicit val backend: SttpBackend[Future, Nothing] = CurlFutureBackend(verbose = true)()
}
