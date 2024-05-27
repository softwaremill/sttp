package sttp.client4

import sttp.client4.ws.SyncWebSocket
import sttp.model.ResponseMetadata
import sttp.shared.Identity
import sttp.ws.WebSocket

trait SttpWebSocketSyncApi {
  def asWebSocket[T](f: SyncWebSocket => T): WebSocketResponseAs[Identity, Either[String, T]] =
    asWebSocketEither(asStringAlways, asWebSocketAlways(f))

  def asWebSocketWithMetadata[T](
      f: (SyncWebSocket, ResponseMetadata) => T
  ): WebSocketResponseAs[Identity, Either[String, T]] =
    asWebSocketEither(asStringAlways, asWebSocketAlwaysWithMetadata(f))

  def asWebSocketAlways[T](f: SyncWebSocket => T): WebSocketResponseAs[Identity, T] =
    asWebSocketAlwaysWithMetadata((w, _) => f(w))

  def asWebSocketAlwaysWithMetadata[T](
      f: (SyncWebSocket, ResponseMetadata) => T
  ): WebSocketResponseAs[Identity, T] =
    WebSocketResponseAs[Identity, T](ResponseAsWebSocket[Identity, T]((ws, m) => f(new SyncWebSocket(ws), m)))

  def asWebSocketUnsafe: WebSocketResponseAs[Identity, Either[String, SyncWebSocket]] =
    asWebSocketEither(asStringAlways, asWebSocketAlwaysUnsafe)

  def asWebSocketAlwaysUnsafe: WebSocketResponseAs[Identity, SyncWebSocket] =
    WebSocketResponseAs[Identity, WebSocket[Identity]](ResponseAsWebSocketUnsafe()).map(new SyncWebSocket(_))

  def fromMetadata[T](
      default: ResponseAs[T],
      conditions: ConditionalResponseAs[WebSocketResponseAs[Identity, T]]*
  ): WebSocketResponseAs[Identity, T] =
    WebSocketResponseAs(ResponseAsFromMetadata(conditions.map(_.map(_.delegate)).toList, default.delegate))

  /** Uses the `onSuccess` response specification for 101 responses (switching protocols) on JVM/Native, 200 responses
    * on JS. Otherwise, use the `onError` specification.
    */
  def asWebSocketEither[A, B](
      onError: ResponseAs[A],
      onSuccess: WebSocketResponseAs[Identity, B]
  ): WebSocketResponseAs[Identity, Either[A, B]] =
    SttpExtensions.asWebSocketEitherPlatform(onError, onSuccess)
}
