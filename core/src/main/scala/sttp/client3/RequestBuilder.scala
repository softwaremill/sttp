package sttp.client3

/** The builder methods of a request. The uri and method are specified.
  *
  * @tparam R
  *   The type of request
  */
trait RequestBuilder[+R <: RequestBuilder[R]] extends PartialRequestBuilder[R, R] { self: R => }
