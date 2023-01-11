package sttp.client3

import sttp.model.Method
import sttp.model.Uri
import sttp.model.Header
import sttp.capabilities.WebSockets
import scala.collection.immutable.Seq

final case class WebSocketStreamRequest[T, S](
    method: Method,
    uri: Uri,
    body: AbstractBody[S],
    headers: Seq[Header],
    response: WebSocketStreamResponseAs[T, S],
    options: RequestOptions,
    tags: Map[String, Any]
) extends AbstractRequest[T, S with WebSockets]
    with RequestBuilder[WebSocketStreamRequest[T, S]] {

  override def showBasic: String = s"$method(web socket) $uri"

  override def method(method: Method, uri: Uri): WebSocketStreamRequest[T, S] = copy(method = method, uri = uri)
  override def withHeaders(headers: Seq[Header]): WebSocketStreamRequest[T, S] = copy(headers = headers)
  override protected def withOptions(options: RequestOptions): WebSocketStreamRequest[T, S] = copy(options = options)
  override protected def copyWithBody(body: BasicBody): WebSocketStreamRequest[T, S] = copy(body = body)
  override protected def withTags(tags: Map[String, Any]): WebSocketStreamRequest[T, S] = copy(tags = tags)

  def mapResponse[T2](f: T => T2): WebSocketStreamRequest[T2, S] =
    copy(response = response.map(f))

  def send[F[_]](backend: SttpBackend[F, WebSockets with S]): F[Response[T]] =
    backend.send(this)
}
