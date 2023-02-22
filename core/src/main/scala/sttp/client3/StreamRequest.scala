package sttp.client3

import sttp.model.Method
import sttp.model.Uri
import sttp.model.Header
import scala.collection.immutable.Seq
import sttp.capabilities.Effect

/** Describes an HTTP stream request: the backend will need to stream the request body, or request response or both.
  *
  * The request can be sent using an instance of [[StreamBackend]].
  *
  * @param response
  *   Description of how the response body should be handled. Needs to be specified upfront so that the response is
  *   always consumed and hence there are no requirements on client code to consume it.
  * @param tags
  *   Request-specific tags which can be used by backends for logging, metrics, etc. Not used by default.
  * @tparam T
  *   The target type, to which the response body should be read.
  * @tparam R
  *   The capabilities required to send this request, it contains the type of stream and an optional effect.
  */
final case class StreamRequest[T, R](
    method: Method,
    uri: Uri,
    body: AbstractBody[R],
    headers: Seq[Header],
    response: StreamResponseAs[T, R],
    options: RequestOptions,
    tags: Map[String, Any]
) extends AbstractRequest[T, R]
    with RequestBuilder[StreamRequest[T, R]] {

  override def showBasic: String = s"$method $uri"

  override def method(method: Method, uri: Uri): StreamRequest[T, R] = copy(method = method, uri = uri)
  override def withHeaders(headers: Seq[Header]): StreamRequest[T, R] = copy(headers = headers)
  override protected def withOptions(options: RequestOptions): StreamRequest[T, R] = copy(options = options)
  override protected def copyWithBody(body: BasicBody): StreamRequest[T, R] = copy(body = body)
  override protected def withTags(tags: Map[String, Any]): StreamRequest[T, R] = copy(tags = tags)

  def response[T2](ra: ResponseAs[T2]): StreamRequest[T2, R] =
    copy(response = new StreamResponseAs(ra.internal))

  /** Specifies the target type to which the response body should be read. Note that this replaces any previous
    * specifications, which also includes any previous `mapResponse` invocations.
    */
  def response[T2, R2 <: R](ra: StreamResponseAs[T2, R2]): StreamRequest[T2, R2] =
    copy(response = ra)

  def response[T2, R2 <: R](ra: WebSocketStreamResponseAs[T2, R2]): WebSocketStreamRequest[T2, R2] =
    WebSocketStreamRequest(method, uri, body, headers, ra, options, tags)

  def mapResponse[T2](f: T => T2): StreamRequest[T2, R] =
    response(response.map(f))

  def send[F[_], P](backend: StreamBackend[F, P])(implicit ev: P with Effect[F] <:< R): F[Response[T]] =
    backend.send(this.asInstanceOf[StreamRequest[T, P with Effect[F]]]) // as witnessed by ev
}
