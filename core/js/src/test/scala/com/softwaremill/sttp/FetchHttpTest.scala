package com.softwaremill.sttp

import scala.concurrent.Future

import com.softwaremill.sttp.testing.AbstractFetchHttpTest
import com.softwaremill.sttp.testing.ConvertToFuture

class FetchHttpTest extends AbstractFetchHttpTest[Future, Nothing] {

  override implicit val backend: SttpBackend[Future, Nothing] = FetchBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

}
