package com.softwaremill.sttp.testing

import scala.concurrent.Future
import scala.language.higherKinds

import com.softwaremill.sttp.Response
import org.scalatest.compatible.Assertion

abstract class AbstractFetchHttpTest[R[_], -S] extends HttpTest[R] {

  override protected def endpoint: String = "localhost:51823"

  // the browser does not give access to redirect responses
  override protected def usesFollowRedirectsBackend: Boolean = false

  override protected def expectRedirectResponse(
      response: R[Response[String]],
      code: Int
  ): Future[Assertion] = {
    response.toFuture().failed.map { t =>
      t.getMessage should be("Unexpected redirect")
    }
  }

  // the fetch spec requires multiple values with the same name to be sorted and joined together...
  // https://fetch.spec.whatwg.org/#concept-header-value
  override protected def cacheControlHeaders = Set("max-age=1000, no-cache")

  // the only way to set the content type is to use a Blob which will default the filename to 'blob'
  override protected def multipartStringDefaultFileName = Some("blob")
}
