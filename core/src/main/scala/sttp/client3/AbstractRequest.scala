package sttp.client3

import sttp.client3.internal.ToCurlConverter
import sttp.client3.internal.ToRfc2616Converter
import sttp.model.Header
import sttp.model.Method
import sttp.model.RequestMetadata
import sttp.model.Uri
import scala.collection.immutable.Seq
import sttp.capabilities.Effect

/** Abstract representation of an HTTP request.
  *
  * The request can be sent using a [[AbstractBackend]] wich provides a superset of the required capabilities.
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

  /** Sends the request, using the given backend. Only requests for which the method & URI are specified can be sent.
    *
    * The required capabilities must be a subset of the capabilities provided by the backend.
    *
    * @return
    *   For synchronous backends (when the effect type is [[Identity]]), [[Response]] is returned directly and
    *   exceptions are thrown. For asynchronous backends (when the effect type is e.g. [[scala.concurrent.Future]]), an
    *   effect containing the [[Response]] is returned. Exceptions are represented as failed effects (e.g. failed
    *   futures).
    *
    * The response body is deserialized as specified by this request (see [[RequestT.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    * unchanged.
    */
  def send[F[+_], P](backend: SttpBackend[F, P])(implicit pEffectFIsR: P with Effect[F] <:< R): F[Response[T]] =
    backend.send(this.asInstanceOf[AbstractRequest[T, P with Effect[F]]]) // as witnessed by pEffectFIsR
}
