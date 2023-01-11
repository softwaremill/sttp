package sttp.client3

import sttp.model.Method
import sttp.model.Uri
import sttp.model.Header
import scala.collection.immutable.Seq

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
  * @tparam S
  *   The type of stream, used to stream the request or response body.
  */
final case class StreamRequest[+T, S](
    method: Method,
    uri: Uri,
    body: AbstractBody[S],
    headers: Seq[Header],
    response: StreamResponseAs[T, S],
    options: RequestOptions,
    tags: Map[String, Any]
) extends AbstractRequest[T, S]
    with RequestBuilder[StreamRequest[T, S]] {

  override def showBasic: String = s"$method $uri"

  override def method(method: Method, uri: Uri): StreamRequest[T, S] = copy(method = method, uri = uri)
  override def withHeaders(headers: Seq[Header]): StreamRequest[T, S] = copy(headers = headers)
  override protected def withOptions(options: RequestOptions): StreamRequest[T, S] = copy(options = options)
  override protected def copyWithBody(body: BasicBody): StreamRequest[T, S] = copy(body = body)
  override protected def withTags(tags: Map[String, Any]): StreamRequest[T, S] = copy(tags = tags)

  def response[T2](ra: ResponseAs[T2]): StreamRequest[T2, R] =
    copy(response = new StreamResponseAs(ra.internal))

  /** Specifies the target type to which the response body should be read. Note that this replaces any previous
    * specifications, which also includes any previous `mapResponse` invocations.
    */
  def response[T2, S2 <: S](ra: StreamResponseAs[T2, S2]): StreamRequest[T2, S2] =
    copy(response = ra)

  def response[T2, S2 <: S](ra: WebSocketStreamResponseAs[T2, S2]): WebSocketStreamRequest[T2, S2] =
    WebSocketStreamRequest(method, uri, body, headers, ra, options, tags)

  def mapResponse[T2](f: T => T2): StreamRequest[T2, S] =
    response(response.map(f))

  def send[F[+_]](backend: SttpBackend[F, S]): F[Response[T]] = backend.send(this)
}
