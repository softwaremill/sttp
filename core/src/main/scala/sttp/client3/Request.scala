package sttp.client3

import sttp.model.Header
import sttp.model.Method
import sttp.model.Uri
import sttp.model.Part
import sttp.capabilities.Streams
import scala.collection.immutable.Seq

/** Describes an HTTP request, along with a description of how the response body should be handled.
  *
  * The request can be sent using an instance of [[SyncBackend]] or [[Backend]].
  *
  * @param response
  *   Description of how the response body should be handled. Needs to be specified upfront so that the response is
  *   always consumed and hence there are no requirements on client code to consume it.
  * @param tags
  *   Request-specific tags which can be used by backends for logging, metrics, etc. Not used by default.
  * @tparam T
  *   The target type, to which the response body should be read.
  */
case class Request[T](
    method: Method,
    uri: Uri,
    body: BasicBody,
    headers: Seq[Header],
    response: ResponseAs[T],
    options: RequestOptions,
    tags: Map[String, Any]
) extends AbstractRequest[T, Any]
    with RequestBuilder[Request[T]] {

  override def showBasic: String = s"$method $uri"

  override def method(method: Method, uri: Uri): Request[T] =
    copy(uri = uri, method = method)
  override def withHeaders(headers: Seq[Header]): Request[T] = copy(headers = headers)
  override protected def copyWithBody(body: BasicBody): Request[T] = copy(body = body)
  override protected def withOptions(options: RequestOptions): Request[T] = copy(options = options)
  override protected def withTags(tags: Map[String, Any]): Request[T] = copy(tags = tags)

  def multipartStreamBody[S](ps: Seq[Part[BodyPart[S]]]): StreamRequest[T, S] =
    StreamRequest(method, uri, MultipartStreamBody(ps), headers, new StreamResponseAs(response.internal), options, tags)

  def multipartStreamBody[S](p1: Part[BodyPart[S]], ps: Part[BodyPart[S]]*): StreamRequest[T, S] =
    StreamRequest(
      method,
      uri,
      MultipartStreamBody(p1 :: ps.toList),
      headers,
      new StreamResponseAs(response.internal),
      options,
      tags
    )

  def streamBody[S](s: Streams[S])(b: s.BinaryStream): StreamRequest[T, S] =
    StreamRequest(method, uri, StreamBody(s)(b), headers, new StreamResponseAs(response.internal), options, tags)

  /** Specifies the target type to which the response body should be read. Note that this replaces any previous
    * specifications, which also includes any previous `mapResponse` invocations.
    */
  def response[T2](ra: ResponseAs[T2]): Request[T2] =
    copy[T2](response = ra)

  def mapResponse[T2](f: T => T2): Request[T2] =
    response(response.map(f))

  def response[F[_], T2](ra: WebSocketResponseAs[F, T2]): WebSocketRequest[F, T2] =
    WebSocketRequest(method, uri, body, headers, ra, options, tags)

  def response[T2, S](ra: StreamResponseAs[T2, S]): StreamRequest[T2, S] =
    StreamRequest(method, uri, body, headers, ra, options, tags)

  def response[T2, S](ra: WebSocketStreamResponseAs[T2, S]): WebSocketStreamRequest[T2, S] =
    WebSocketStreamRequest(method, uri, body, headers, ra, options, tags)

  /** Sends the request, using the given backend.
    *
    * @return
    *   For synchronous backends [[Response]] is returned directly and exceptions are thrown. For asynchronous backends
    *   (when the effect type is e.g. [[scala.concurrent.Future]]), an effect containing the [[Response]] is returned.
    *   Exceptions are represented as failed effects (e.g. failed futures).
    *
    * The response body is deserialized as specified by this request (see [[Request.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    * unchanged.
    */
  def send[F[_]](backend: SttpBackend[F, Any]): F[Response[T]] = backend.send(this)
}

object Request {
  implicit class RichRequestTEither[A, B](r: Request[Either[A, B]]) {
    def mapResponseRight[B2](f: B => B2): Request[Either[A, B2]] = r.copy(response = r.response.mapRight(f))
    def responseGetRight: Request[B] = r.copy(response = r.response.getRight)
  }

  implicit class RichRequestTEitherResponseException[HE, DE, B](
      r: Request[Either[ResponseException[HE, DE], B]]
  ) {
    def responseGetEither: Request[Either[HE, B]] = r.copy(response = r.response.getEither)
  }
}
