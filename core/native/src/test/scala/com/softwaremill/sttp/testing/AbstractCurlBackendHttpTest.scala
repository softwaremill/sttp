package com.softwaremill.sttp.testing

import com.softwaremill.sttp._
import org.scalatest.compatible.Assertion

import scala.concurrent.Future
import scala.language.higherKinds

abstract class AbstractCurlBackendHttpTest[-S] extends NativeHttpTest {

  override protected def endpoint: String = "localhost:51823"

//  override protected def expectRedirectResponse(response: Response[String], code: Int): Future[Assertion] = {
//    response.toFuture().failed.map { t =>
//      t.getMessage should be("Unexpected redirect")
//    }
//  }

  // the only way to set the content type is to use a Blob which has a default filename of 'blob'
  override protected def multipartStringDefaultFileName = Some("blob")

}
