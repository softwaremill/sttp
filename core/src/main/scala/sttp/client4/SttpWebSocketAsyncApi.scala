package sttp.client4

import sttp.model.ResponseMetadata
import sttp.ws.WebSocket

trait SttpWebSocketAsyncApi {

  /** Handles the response body by either reading a string (for non-2xx responses), or otherwise providing an open
    * [[WebSocket]] instance to the `f` function.
    *
    * The effect type used by `f` must be compatible with the effect type of the backend. The web socket is always
    * closed after `f` completes.
    */
  def asWebSocket[F[_], T](f: WebSocket[F] => F[T]): WebSocketResponseAs[F, Either[String, T]] =
    asWebSocketEither(asStringAlways, asWebSocketAlways(f))

  /** Handles the response as a web socket, providing an open [[WebSocket]] instance to the `f` function, if the status
    * code is 2xx. Otherwise, returns a failed effect (with [[HttpError]]).
    *
    * The effect type used by `f` must be compatible with the effect type of the backend. The web socket is always
    * closed after `f` completes.
    *
    * @see
    *   the [[ResponseAs#orFail]] method can be used to convert any response description which returns an `Either` into
    *   an exception-throwing variant.
    */
  def asWebSocketOrFail[F[_], T](f: WebSocket[F] => F[T]): WebSocketResponseAs[F, T] =
    asWebSocket(f).orFail.showAs("as web socket or fail")

  /** Handles the response body by either reading a string (for non-2xx responses), or otherwise providing an open
    * [[WebSocket]] instance, along with the response metadata, to the `f` function.
    *
    * The effect type used by `f` must be compatible with the effect type of the backend. The web socket is always
    * closed after `f` completes.
    */
  def asWebSocketWithMetadata[F[_], T](
      f: (WebSocket[F], ResponseMetadata) => F[T]
  ): WebSocketResponseAs[F, Either[String, T]] =
    asWebSocketEither(asStringAlways, asWebSocketAlwaysWithMetadata(f))

  /** Handles the response body by providing an open [[WebSocket]] instance to the `f` function, regardless of the
    * status code.
    *
    * The effect type used by `f` must be compatible with the effect type of the backend. The web socket is always
    * closed after `f` completes.
    */
  def asWebSocketAlways[F[_], T](f: WebSocket[F] => F[T]): WebSocketResponseAs[F, T] =
    asWebSocketAlwaysWithMetadata((w, _) => f(w))

  /** Handles the response body by providing an open [[WebSocket]] instance to the `f` function, along with the response
    * metadata, regardless of the status code.
    *
    * The effect type used by `f` must be compatible with the effect type of the backend. The web socket is always
    * closed after `f` completes.
    */
  def asWebSocketAlwaysWithMetadata[F[_], T](f: (WebSocket[F], ResponseMetadata) => F[T]): WebSocketResponseAs[F, T] =
    WebSocketResponseAs(ResponseAsWebSocket(f))

  /** Handles the response body by either reading a string (for non-2xx responses), or otherwise returning an open
    * [[WebSocket]] instance. It is the responsibility of the caller to consume & close the web socket.
    *
    * The effect type `F` must be compatible with the effect type of the backend.
    */
  def asWebSocketUnsafe[F[_]]: WebSocketResponseAs[F, Either[String, WebSocket[F]]] =
    asWebSocketEither(asStringAlways, asWebSocketAlwaysUnsafe)

  /** Handles the response body by returning an open [[WebSocket]] instance, regardless of the status code. It is the
    * responsibility of the caller to consume & close the web socket.
    *
    * The effect type `F` must be compatible with the effect type of the backend.
    */
  def asWebSocketAlwaysUnsafe[F[_]]: WebSocketResponseAs[F, WebSocket[F]] =
    WebSocketResponseAs(ResponseAsWebSocketUnsafe())

  /** Uses the [[ResponseAs]] description that matches the condition (using the response's metadata).
    *
    * This allows using different response description basing on the status code, for example. If none of the conditions
    * match, the default response handling description is used.
    *
    * The effect type `F` must be compatible with the effect type of the backend.
    */
  def fromMetadata[F[_], T](
      default: ResponseAs[T],
      conditions: ConditionalResponseAs[WebSocketResponseAs[F, T]]*
  ): WebSocketResponseAs[F, T] =
    WebSocketResponseAs(ResponseAsFromMetadata(conditions.map(_.map(_.delegate)).toList, default.delegate))

  /** Uses the `onSuccess` response description for 101 responses (switching protocols) on JVM/Native, 200 responses on
    * JS. Otherwise, use the `onError` description.
    *
    * The effect type `F` must be compatible with the effect type of the backend.
    */
  def asWebSocketEither[F[_], A, B](
      onError: ResponseAs[A],
      onSuccess: WebSocketResponseAs[F, B]
  ): WebSocketResponseAs[F, Either[A, B]] =
    SttpExtensions.asWebSocketEitherPlatform(onError, onSuccess)
}
