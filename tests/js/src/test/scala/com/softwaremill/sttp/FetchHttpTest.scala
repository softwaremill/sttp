package com.softwaremill.sttp

import com.softwaremill.sttp.testing.ConvertToFuture
import com.softwaremill.sttp.testing.HttpTest
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.scalatest.Assertion

class FetchHttpTest extends HttpTest[Future] {
  implicit override def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  override implicit val backend: SttpBackend[Future, Nothing] =
    FetchBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] =
    ConvertToFuture.future

  // the browser does not give access to redirect responses
  override protected def usesFollowRedirectsBackend: Boolean = false

  override protected def expectRedirectResponse(
      response: Future[Response[String]],
      code: Int
  ): Future[Assertion] = {
    response.failed.map { t =>
      t.getMessage should be("Unexpected redirect")
    }
  }

  // the fetch spec requires multiple values with the same name to be sorted and joined together...
  // https://fetch.spec.whatwg.org/#concept-header-value
  override protected def cacheControlHeaders = Set("max-age=1000, no-cache")

  // the only way to set the content type is to use a Blob which will default the filename to 'blob'
  override protected def multipartStringDefaultFileName = Some("blob")

}
