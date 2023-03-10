package sttp.client4

import sttp.model.{Header, Method, Part, RequestMetadata, Uri}
import sttp.capabilities.{Effect, Streams, WebSockets}
import sttp.client4.internal.{ToCurlConverter, ToRfc2616Converter}

import scala.collection.immutable.Seq

/** A generic description of an HTTP request, along with a description of how the response body should be handled.
  *
  * Client code should use concrete subtypes of this trait. The [[Request.send]], [[StreamRequest.send]],
  * [[WebSocketRequest.send]] or [[WebSocketStreamRequest.send]] methods should be used to send the request, providing
  * an instance of the appropriate [[Backend]] subtype.
  *
  * However, the request can aso be sent using the [[GenericBackend.send]] method, which provides a superset of the
  * required capabilities.
  *
  * @tparam T
  *   The target type, to which the response body should be read.
  * @tparam R
  *   The backend capabilities required by the request or response description. This might be `Any` (no requirements),
  *   [[sttp.capabilities.Effect]] (the backend must support the given effect type), [[sttp.capabilities.Streams]] (the
  *   ability to send and receive streaming bodies) or [[sttp.capabilities.WebSockets]] (the ability to handle websocket
  *   requests).
  */
trait GenericRequest[+T, -R] extends RequestBuilder[GenericRequest[T, R]] with RequestMetadata {
  def body: GenericRequestBody[R]
  def response: ResponseAsDelegate[T, R]
  def mapResponse[T2](f: T => T2): GenericRequest[T2, R]

  def toCurl: String = ToCurlConverter(this)
  def toCurl(sensitiveHeaders: Set[String]): String = ToCurlConverter(this, sensitiveHeaders)

  def toRfc2616Format: String = ToRfc2616Converter.requestToRfc2616(this)
  def toRfc2616Format(sensitiveHeaders: Set[String]): String =
    ToRfc2616Converter.requestToRfc2616(this, sensitiveHeaders)

  /** Metadata of the request, which doesn't retain the request body, or the response handling specification. */
  def onlyMetadata: RequestMetadata = {
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

//

/** Describes an HTTP request, along with a description of how the response body should be handled.
  *
  * The request can be sent using an instance of [[SyncBackend]] or [[Backend]] with the [[send]] method.
  *
  * @param response
  *   Description of how the response body should be handled. Needs to be specified upfront so that the response is
  *   always consumed and hence there are no requirements on client code to consume it.
  * @param tags
  *   Request-specific tags which can be used by backends for logging, metrics, etc. Empty by default.
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
) extends GenericRequest[T, Any]
    with RequestBuilder[Request[T]] {

  override def showBasic: String = s"$method $uri"

  override def method(method: Method, uri: Uri): Request[T] = copy(uri = uri, method = method)
  override def withHeaders(headers: Seq[Header]): Request[T] = copy(headers = headers)
  override def withOptions(options: RequestOptions): Request[T] = copy(options = options)
  override def withTags(tags: Map[String, Any]): Request[T] = copy(tags = tags)
  override protected def copyWithBody(body: BasicBody): Request[T] = copy(body = body)

  def multipartStreamBody[S](ps: Seq[Part[BodyPart[S]]]): StreamRequest[T, S] =
    StreamRequest(method, uri, MultipartStreamBody(ps), headers, StreamResponseAs(response.delegate), options, tags)

  def multipartStreamBody[S](p1: Part[BodyPart[S]], ps: Part[BodyPart[S]]*): StreamRequest[T, S] =
    StreamRequest(
      method,
      uri,
      MultipartStreamBody(p1 :: ps.toList),
      headers,
      StreamResponseAs(response.delegate),
      options,
      tags
    )

  def streamBody[S](s: Streams[S])(b: s.BinaryStream): StreamRequest[T, S] =
    StreamRequest(method, uri, StreamBody(s)(b), headers, StreamResponseAs(response.delegate), options, tags)

  /** Specifies the target type to which the response body should be read. Note that this replaces any previous
    * specifications, which also includes any previous `mapResponse` invocations.
    */
  def response[T2](ra: ResponseAs[T2]): Request[T2] = copy[T2](response = ra)

  def mapResponse[T2](f: T => T2): Request[T2] = response(response.map(f))

  /** Specifies that this is a WebSocket request. A [[WebSocketBackend]] will be required to send this request. */
  def response[F[_], T2](ra: WebSocketResponseAs[F, T2]): WebSocketRequest[F, T2] =
    WebSocketRequest(method, uri, body, headers, ra, options, tags)

  /** Specifies that the response body should be processed using a non-blocking, asynchronous stream, as witnessed by
    * the `S` capability. A [[StreamBackend]] will be required to send this request.
    */
  def response[T2, S](ra: StreamResponseAs[T2, S]): StreamRequest[T2, S] =
    StreamRequest(method, uri, body, headers, ra, options, tags)

  /** Specifies that this is a WebSocket request, and the WebSocket will be processed using a non-blocking, asynchronous
    * stream, as witnessed by the `S` capability. A [[WebSocketStreamBackend]] will be required to send this request.
    */
  def response[T2, S](ra: WebSocketStreamResponseAs[T2, S]): WebSocketStreamRequest[T2, S] =
    WebSocketStreamRequest(method, uri, body, headers, ra, options, tags)

  /** Sends the request, using the given backend.
    *
    * @return
    *   An `F`-effect, containing a [[Response]], with the body handled as specified by this request (see
    *   [[Request.response]]). Effects might include asynchronous computations (e.g. [[scala.concurrent.Future]]), pure
    *   effect descriptions (`IO`), or error wrappers (e.g. [[TryBackend]]). Exceptions are represented as failed
    *   effects (e.g. failed futures).
    *
    * The response body is deserialized as specified by this request (see [[Request.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    * unchanged.
    */
  def send[F[_]](backend: Backend[F]): F[Response[T]] = backend.send(this)

  /** Sends the request synchronously, using the given backend.
    *
    * @return
    *   A [[Response]], with the body handled as specified by this request (see [[Request.response]]).
    *
    * The response body is deserialized as specified by this request (see [[Request.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    * unchanged.
    */
  def send(backend: SyncBackend): Response[T] = backend.send(this)
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

//

/** Describes an HTTP request, along with a description of how the response body should be handled. Either the request
  * or response body uses non-blocking, asynchronous streams.
  *
  * The request can be sent using an instance of [[StreamBackend]] with the [[send]] method.
  *
  * @param response
  *   Description of how the response body should be handled. Needs to be specified upfront so that the response is
  *   always consumed and hence there are no requirements on client code to consume it.
  * @param tags
  *   Request-specific tags which can be used by backends for logging, metrics, etc. Empty by default.
  * @tparam T
  *   The target type, to which the response body should be read. If the response body is streamed, this might be the
  *   value obtained by processing the entire stream.
  * @tparam R
  *   The capabilities required to send this request: a subtype of [[Streams]], and optionally an [[Effect]].
  */
final case class StreamRequest[T, R](
                                      method: Method,
                                      uri: Uri,
                                      body: GenericRequestBody[R],
                                      headers: Seq[Header],
                                      response: StreamResponseAs[T, R],
                                      options: RequestOptions,
                                      tags: Map[String, Any]
) extends GenericRequest[T, R]
    with RequestBuilder[StreamRequest[T, R]] {

  override def showBasic: String = s"$method $uri"

  override def method(method: Method, uri: Uri): StreamRequest[T, R] = copy(method = method, uri = uri)
  override def withHeaders(headers: Seq[Header]): StreamRequest[T, R] = copy(headers = headers)
  override def withOptions(options: RequestOptions): StreamRequest[T, R] = copy(options = options)
  override def withTags(tags: Map[String, Any]): StreamRequest[T, R] = copy(tags = tags)
  override protected def copyWithBody(body: BasicBody): StreamRequest[T, R] = copy(body = body)

  /** Specifies the target type to which the response body should be read. Note that this replaces any previous
    * specifications, which also includes any previous `mapResponse` invocations.
    */
  def response[T2](ra: ResponseAs[T2]): StreamRequest[T2, R] = copy(response = new StreamResponseAs(ra.delegate))

  /** Specifies that the response body should be processed using a non-blocking, asynchronous stream, as witnessed by
    * the `R2` capability. This capability must be a subset of any capabilities required by previously (`R`). A
    * [[StreamBackend]] will be required to send this request.
    */
  def response[T2, R2 <: R](ra: StreamResponseAs[T2, R2]): StreamRequest[T2, R2] = copy(response = ra)

  /** Specifies that this is a WebSocket request. A [[WebSocketStreamBackend]] will be required to send this request. */
  def response[T2, F[_]](ra: WebSocketResponseAs[F, T2]): WebSocketStreamRequest[T2, Effect[F] with R] =
    WebSocketStreamRequest(
      method,
      uri,
      body,
      headers,
      WebSocketStreamResponseAs[T2, Effect[F] with R](ra.delegate),
      options,
      tags
    )

  def mapResponse[T2](f: T => T2): StreamRequest[T2, R] = copy(response = response.map(f))

  /** Sends the request, using the given backend.
    *
    * The required capabilities `R` must be a subset of the capabilities `P` provided by the backend. That is, the
    * supported streams type must match, as well as the optional effect type.
    *
    * @return
    *   An `F`-effect, containing a [[Response]], with the body handled as specified by this request (see
    *   [[Request.response]]). Effects might include asynchronous computations (e.g. [[scala.concurrent.Future]]), pure
    *   effect descriptions (`IO`), or error wrappers (e.g. [[TryBackend]]). Exceptions are represented as failed
    *   effects (e.g. failed futures).
    *
    * The response body is deserialized as specified by this request (see [[Request.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    * unchanged.
    */
  def send[F[_], P](backend: StreamBackend[F, P])(implicit ev: P with Effect[F] <:< R): F[Response[T]] =
    backend.send(this.asInstanceOf[StreamRequest[T, P with Effect[F]]]) // as witnessed by ev
}

//

/** Describes an HTTP WebSocket request.
  *
  * The request can be sent using an instance of [[WebSocketBackend]] with the [[send]] method.
  *
  * @param response
  *   Description of how the WebSocket should be handled. Needs to be specified upfront so that the response is always
  *   consumed and hence there are no requirements on client code to consume it.
  * @param tags
  *   Request-specific tags which can be used by backends for logging, metrics, etc. Empty by default.
  * @tparam F
  *   The effect type used to process the WebSocket. Might include asynchronous computations (e.g.
  *   [[scala.concurrent.Future]]), pure effect descriptions (`IO`), or synchronous computations ([[Identity]]).
  * @tparam T
  *   The target type, to which the response body should be read. If the WebSocket interactions are described entirely
  *   by the response description, this might be `Unit`. Otherwise, this can be a [[sttp.ws.WebSocket]] instance.
  */
final case class WebSocketRequest[F[_], T](
    method: Method,
    uri: Uri,
    body: BasicBody,
    headers: Seq[Header],
    response: WebSocketResponseAs[F, T],
    options: RequestOptions,
    tags: Map[String, Any]
) extends GenericRequest[T, WebSockets with Effect[F]]
    with RequestBuilder[WebSocketRequest[F, T]] {

  override def showBasic: String = s"$method (WebSocket) $uri"

  override def method(method: Method, uri: Uri): WebSocketRequest[F, T] = copy(method = method, uri = uri)
  override def withHeaders(headers: Seq[Header]): WebSocketRequest[F, T] = copy(headers = headers)
  override def withOptions(options: RequestOptions): WebSocketRequest[F, T] = copy(options = options)
  override def withTags(tags: Map[String, Any]): WebSocketRequest[F, T] = copy(tags = tags)
  override protected def copyWithBody(body: BasicBody): WebSocketRequest[F, T] = copy(body = body)

  def streamBody[S](s: Streams[S])(b: s.BinaryStream): WebSocketStreamRequest[T, Effect[F] with S] =
    WebSocketStreamRequest(
      method,
      uri,
      StreamBody(s)(b),
      headers,
      WebSocketStreamResponseAs[T, Effect[F] with S](response.delegate),
      options,
      tags
    )

  def mapResponse[T2](f: T => T2): WebSocketRequest[F, T2] = copy(response = response.map(f))

  /** Sends the WebSocket request, using the given backend.
    *
    * @return
    *   An `F`-effect, containing a [[Response]], with the body handled as specified by this request (see
    *   [[Request.response]]). Effects might include asynchronous computations (e.g. [[scala.concurrent.Future]]), pure
    *   effect descriptions (`IO`), or error wrappers (e.g. [[TryBackend]]). Exceptions are represented as failed
    *   effects (e.g. failed futures).
    *
    * The response WebSocket is handled as specified by this request (see [[Request.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    * unchanged.
    */
  def send(backend: WebSocketBackend[F]): F[Response[T]] = backend.send(this)
}

//

/** Describes an HTTP WebSocket request. Either the request body, or the WebSocket handling uses non-blocking,
  * asynchronous streams.
  *
  * The request can be sent using an instance of [[WebSocketStreamBackend]] with the [[send]] method.
  *
  * @param response
  *   Description of how the WebSocket should be handled. Needs to be specified upfront so that the response is always
  *   consumed and hence there are no requirements on client code to consume it.
  * @param tags
  *   Request-specific tags which can be used by backends for logging, metrics, etc. Empty by default.
  * @tparam T
  *   The target type, to which the response body should be read. If the WebSocket interactions are described entirely
  *   by the response description, this might be `Unit`. Otherwise, this can be an `S` stream of frames or mapped
  *   WebSocket messages.
  * @tparam S
  *   The stream capability required to send this request, a subtype of [[Streams]].
  */
final case class WebSocketStreamRequest[T, S](
                                               method: Method,
                                               uri: Uri,
                                               body: GenericRequestBody[S],
                                               headers: Seq[Header],
                                               response: WebSocketStreamResponseAs[T, S],
                                               options: RequestOptions,
                                               tags: Map[String, Any]
) extends GenericRequest[T, S with WebSockets]
    with RequestBuilder[WebSocketStreamRequest[T, S]] {

  override def showBasic: String = s"$method (WebSocket) $uri"

  override def method(method: Method, uri: Uri): WebSocketStreamRequest[T, S] = copy(method = method, uri = uri)
  override def withHeaders(headers: Seq[Header]): WebSocketStreamRequest[T, S] = copy(headers = headers)
  override def withOptions(options: RequestOptions): WebSocketStreamRequest[T, S] = copy(options = options)
  override def withTags(tags: Map[String, Any]): WebSocketStreamRequest[T, S] = copy(tags = tags)
  override protected def copyWithBody(body: BasicBody): WebSocketStreamRequest[T, S] = copy(body = body)

  def mapResponse[T2](f: T => T2): WebSocketStreamRequest[T2, S] = copy(response = response.map(f))

  /** Sends the WebSocket request, using the given backend.
    *
    * The required streams capability `S` must match the streams supported by the backend.
    *
    * @return
    *   An `F`-effect, containing a [[Response]], with the body handled as specified by this request (see
    *   [[Request.response]]). Effects might include asynchronous computations (e.g. [[scala.concurrent.Future]]), pure
    *   effect descriptions (`IO`), or error wrappers (e.g. [[TryBackend]]). Exceptions are represented as failed
    *   effects (e.g. failed futures).
    *
    * The response WebSocket is handled as specified by this request (see [[Request.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    * unchanged.
    */
  def send[F[_]](backend: WebSocketStreamBackend[F, S]): F[Response[T]] = backend.send(this)
}
