package sttp.client3

import sttp.client3.internal.ToCurlConverter
import sttp.client3.internal.ToRfc2616Converter
import sttp.model.Header
import sttp.model.Method
import sttp.model.RequestMetadata
import sttp.model.Uri
import scala.collection.immutable.Seq

/** Abstract representation of an HTTP request.
  *
  * The request can be sent using a [[GenericBackend]] wich provides a superset of the required capabilities.
  *
  * @tparam T
  *   The target type, to which the response body should be read.
  * @tparam R
  *   The backend capabilities required by the request or response description. This might be `Any` (no requirements),
  *   [[sttp.capabilities.Effect]] (the backend must support the given effect type), [[sttp.capabilities.Streams]] (the
  *   ability to send and receive streaming bodies) or [[sttp.capabilities.WebSockets]] (the ability to handle websocket
  *   requests).
  */
trait AbstractRequest[+T, -R] extends RequestBuilder[AbstractRequest[T, R]] {
  def method: Method
  def uri: Uri
  def body: AbstractBody[R]
  def response: AbstractResponseAs[T, R]
  def mapResponse[T2](f: T => T2): AbstractRequest[T2, R]

  def toCurl: String = ToCurlConverter(this)
  def toCurl(sensitiveHeaders: Set[String]): String =
    ToCurlConverter(this, sensitiveHeaders)

  def toRfc2616Format: String = ToRfc2616Converter.requestToRfc2616(this)
  def toRfc2616Format(sensitiveHeaders: Set[String]): String =
    ToRfc2616Converter.requestToRfc2616(this, sensitiveHeaders)

  private[client3] def onlyMetadata: RequestMetadata = {
    val m = method
    val u = uri
    val h = headers
    new RequestMetadata {
      override val method: Method = m
      override val uri: Uri = u
      override val headers: Seq[Header] = h
    }
  }

  def isWebSocket: Boolean = (this: Any) match {
    case _: WebSocketRequest[_, _]       => true
    case _: WebSocketStreamRequest[_, _] => true
    case _                               => false
  }
}
