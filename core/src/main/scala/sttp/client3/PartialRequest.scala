package sttp.client3

import sttp.model.Header
import sttp.model.Method
import sttp.model.Uri
import scala.collection.immutable.Seq

/** Describes a partial HTTP request, along with a description of how the response body should be handled. A partial
  * request cannot be sent because the method and uri are not yet specified.
  *
  * @param response
  *   Description of how the response body should be handled. Needs to be specified upfront so that the response is
  *   always consumed and hence there are no requirements on client code to consume it.
  * @param tags
  *   Request-specific tags which can be used by backends for logging, metrics, etc. Empty by default.
  * @tparam T
  *   The target type, to which the response body should be read.
  */
final case class PartialRequest[T](
    body: BasicBody,
    headers: Seq[Header],
    response: ResponseAs[T],
    options: RequestOptions,
    tags: Map[String, Any]
) extends PartialRequestBuilder[PartialRequest[T], Request[T]] {

  override def showBasic: String = "(no method & uri set)"

  override def method(method: Method, uri: Uri): Request[T] =
    Request(method, uri, body, headers, response, options, tags)
  override def withHeaders(headers: Seq[Header]): PartialRequest[T] = copy(headers = headers)
  override def withOptions(options: RequestOptions): PartialRequest[T] = copy(options = options)
  override def withTags(tags: Map[String, Any]): PartialRequest[T] = copy(tags = tags)
  override protected def copyWithBody(body: BasicBody): PartialRequest[T] = copy(body = body)
  def response[T2](ra: ResponseAs[T2]): PartialRequest[T2] = copy(response = ra)
}
