package sttp.client3.testing

import org.scalatest.compatible.Assertion
import sttp.client3.Response

import scala.concurrent.Future
import scala.language.higherKinds

abstract class AbstractFetchHttpTest[F[_], +P] extends HttpTest[F] {
  override protected def expectRedirectResponse(
      response: F[Response[String]],
      code: Int
  ): Future[Assertion] = {
    response.toFuture().failed.map { t =>
      t.getMessage should be("Unexpected redirect")
    }
  }

  // the fetch spec requires multiple values with the same name to be sorted and joined together...
  // https://fetch.spec.whatwg.org/#concept-header-value
  override protected def cacheControlHeaders = Set("max-age=1000, no-cache")

  // the only way to set the content type is to use a Blob which has a default filename of 'blob'
  override protected def multipartStringDefaultFileName: Option[String] = Some("blob")

  // everything is reported as "scala.scalajs.js.JavaScriptException: TypeError: Failed to fetch"
  override protected def supportsSttpExceptions = false
}
