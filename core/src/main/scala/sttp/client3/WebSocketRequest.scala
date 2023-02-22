package sttp.client3

import sttp.model.Method
import sttp.model.Uri
import sttp.model.Header
import sttp.capabilities.Effect
import sttp.capabilities.WebSockets
import scala.collection.immutable.Seq

/** Describes an HTTP websocket request.
  *
  * The request can be sent using an instance of [[WebSocketBackend]].
  *
  * @param response
  *   Description of how the websocket should be handled. Needs to be specified upfront so that the response is always
  *   consumed and hence there are no requirements on client code to consume it.
  * @param tags
  *   Request-specific tags which can be used by backends for logging, metrics, etc. Not used by default.
  * @tparam F
  *   The effect type used to process the websocket.
  * @tparam T
  *   The target type, to which the response body should be read.
  */
final case class WebSocketRequest[F[_], T](
    method: Method,
    uri: Uri,
    body: BasicBody,
    headers: Seq[Header],
    response: WebSocketResponseAs[F, T],
    options: RequestOptions,
    tags: Map[String, Any]
) extends AbstractRequest[T, WebSockets with Effect[F]]
    with RequestBuilder[WebSocketRequest[F, T]] {

  override def showBasic: String = s"$method(web socket) $uri"

  override def method(method: Method, uri: Uri): WebSocketRequest[F, T] = copy(method = method, uri = uri)
  override def withHeaders(headers: Seq[Header]): WebSocketRequest[F, T] = copy(headers = headers)
  override protected def withOptions(options: RequestOptions): WebSocketRequest[F, T] = copy(options = options)
  override protected def copyWithBody(body: BasicBody): WebSocketRequest[F, T] = copy(body = body)
  override protected def withTags(tags: Map[String, Any]): WebSocketRequest[F, T] = copy(tags = tags)

  def mapResponse[T2](f: T => T2): WebSocketRequest[F, T2] =
    copy(response = response.map(f))

  def send(backend: WebSocketBackend[F]): F[Response[T]] = backend.internalSend(this)
}
