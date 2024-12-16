package sttp.client4.json

import sttp.client4.ResponseAs
import sttp.client4.MappedResponseAs
import sttp.client4.ResponseAsByteArray
import sttp.client4.internal.Utf8
import sttp.model.ResponseMetadata
import sttp.model.StatusCode
import org.scalatest.Assertions.fail

object RunResponseAs {
  def apply[A](
      responseAs: ResponseAs[A],
      responseMetadata: ResponseMetadata = ResponseMetadata(StatusCode.Ok, "", Nil)
  ): String => A =
    responseAs.delegate match {
      case responseAs: MappedResponseAs[_, A, Nothing] @unchecked =>
        responseAs.raw match {
          case ResponseAsByteArray =>
            s => responseAs.g(s.getBytes(Utf8), responseMetadata)
          case _ =>
            fail("MappedResponseAs does not wrap a ResponseAsByteArray")
        }
      case _ => fail("ResponseAs is not a MappedResponseAs")
    }
}
