package sttp.client4

import sttp.model.ResponseMetadata
import sttp.ws.WebSocket

trait SttpWebSocketAsyncApi {
  def asWebSocket[F[_], T](f: WebSocket[F] => F[T]): WebSocketResponseAs[F, Either[String, T]] =
    asWebSocketEither(asStringAlways, asWebSocketAlways(f))

  def asWebSocketWithMetadata[F[_], T](
      f: (WebSocket[F], ResponseMetadata) => F[T]
  ): WebSocketResponseAs[F, Either[String, T]] =
    asWebSocketEither(asStringAlways, asWebSocketAlwaysWithMetadata(f))

  def asWebSocketAlways[F[_], T](f: WebSocket[F] => F[T]): WebSocketResponseAs[F, T] =
    asWebSocketAlwaysWithMetadata((w, _) => f(w))

  def asWebSocketAlwaysWithMetadata[F[_], T](f: (WebSocket[F], ResponseMetadata) => F[T]): WebSocketResponseAs[F, T] =
    WebSocketResponseAs(ResponseAsWebSocket(f))

  def asWebSocketUnsafe[F[_]]: WebSocketResponseAs[F, Either[String, WebSocket[F]]] =
    asWebSocketEither(asStringAlways, asWebSocketAlwaysUnsafe)

  def asWebSocketAlwaysUnsafe[F[_]]: WebSocketResponseAs[F, WebSocket[F]] =
    WebSocketResponseAs(ResponseAsWebSocketUnsafe())

  def fromMetadata[F[_], T](
      default: ResponseAs[T],
      conditions: ConditionalResponseAs[WebSocketResponseAs[F, T]]*
  ): WebSocketResponseAs[F, T] =
    WebSocketResponseAs(ResponseAsFromMetadata(conditions.map(_.map(_.delegate)).toList, default.delegate))

  /** Uses the `onSuccess` response specification for 101 responses (switching protocols) on JVM/Native, 200 responses
    * on JS. Otherwise, use the `onError` specification.
    */
  def asWebSocketEither[F[_], A, B](
      onError: ResponseAs[A],
      onSuccess: WebSocketResponseAs[F, B]
  ): WebSocketResponseAs[F, Either[A, B]] =
    SttpExtensions.asWebSocketEitherPlatform(onError, onSuccess)
}
