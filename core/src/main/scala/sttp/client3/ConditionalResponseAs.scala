package sttp.client3

import sttp.model.ResponseMetadata

/** A wrapper around a ResponseAs to supplement it with a condition on the response metadata.
  *
  * Used in [[SttpApi.fromMetdata]] to condition the response handler upon the response metadata: status code, headers,
  * etc.
  *
  * @tparam R
  *   The type of response
  */
case class ConditionalResponseAs[+R](condition: ResponseMetadata => Boolean, responseAs: R) {
  def map[R2](f: R => R2): ConditionalResponseAs[R2] = ConditionalResponseAs(condition, f(responseAs))
}
