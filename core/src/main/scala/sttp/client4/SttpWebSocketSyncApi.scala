package sttp.client4

import sttp.client4.ws.SyncWebSocket
import sttp.model.ResponseMetadata
import sttp.shared.Identity
import sttp.ws.WebSocket

trait SttpWebSocketSyncApi {

  /** Handles the response body by either reading a string (for non-2xx responses), or otherwise providing an open
    * [[WebSocket]] instance to the `f` function.
    *
    * The web socket is always closed after `f` completes.
    */
  def asWebSocket[T](f: SyncWebSocket => T): WebSocketResponseAs[Identity, Either[String, T]] =
    asWebSocketEither(asStringAlways, asWebSocketAlways(f))

  /** Handles the response as a web socket, providing an open [[WebSocket]] instance to the `f` function, if the status
    * code is 2xx. Otherwise, throws an [[UnexpectedStatusCode]].
    *
    * The web socket is always closed after `f` completes.
    *
    * @see
    *   the [[ResponseAs#orFail]] method can be used to convert any response description which returns an `Either` into
    *   an exception-throwing variant.
    */
  def asWebSocketOrFail[T](f: SyncWebSocket => T): WebSocketResponseAs[Identity, T] =
    asWebSocket(f).orFail.showAs("as web socket or fail")

  /** Handles the response body by either reading a string (for non-2xx responses), or otherwise providing an open
    * [[WebSocket]] instance, along with the response metadata, to the `f` function.
    *
    * The web socket is always closed after `f` completes.
    */
  def asWebSocketWithMetadata[T](
      f: (SyncWebSocket, ResponseMetadata) => T
  ): WebSocketResponseAs[Identity, Either[String, T]] =
    asWebSocketEither(asStringAlways, asWebSocketAlwaysWithMetadata(f))

  /** Handles the response body by providing an open [[WebSocket]] instance to the `f` function, regardless of the
    * status code.
    *
    * The web socket is always closed after `f` completes.
    */
  def asWebSocketAlways[T](f: SyncWebSocket => T): WebSocketResponseAs[Identity, T] =
    asWebSocketAlwaysWithMetadata((w, _) => f(w))

  /** Handles the response body by providing an open [[WebSocket]] instance to the `f` function, along with the response
    * metadata, regardless of the status code.
    *
    * The web socket is always closed after `f` completes.
    */
  def asWebSocketAlwaysWithMetadata[T](
      f: (SyncWebSocket, ResponseMetadata) => T
  ): WebSocketResponseAs[Identity, T] =
    WebSocketResponseAs[Identity, T](ResponseAsWebSocket[Identity, T]((ws, m) => f(new SyncWebSocket(ws), m)))

  /** Handles the response body by either reading a string (for non-2xx responses), or otherwise returning an open
    * [[WebSocket]] instance. It is the responsibility of the caller to consume & close the web socket.
    */
  def asWebSocketUnsafe: WebSocketResponseAs[Identity, Either[String, SyncWebSocket]] =
    asWebSocketEither(asStringAlways, asWebSocketAlwaysUnsafe)

  /** Handles the response body by returning an open [[WebSocket]] instance, regardless of the status code. It is the
    * responsibility of the caller to consume & close the web socket.
    */
  def asWebSocketAlwaysUnsafe: WebSocketResponseAs[Identity, SyncWebSocket] =
    WebSocketResponseAs[Identity, WebSocket[Identity]](ResponseAsWebSocketUnsafe()).map(new SyncWebSocket(_))

  /** Uses the [[ResponseAs]] description that matches the condition (using the response's metadata).
    *
    * This allows using different response description basing on the status code, for example. If none of the conditions
    * match, the default response handling description is used.
    */
  def fromMetadata[T](
      default: ResponseAs[T],
      conditions: ConditionalResponseAs[WebSocketResponseAs[Identity, T]]*
  ): WebSocketResponseAs[Identity, T] =
    WebSocketResponseAs(ResponseAsFromMetadata(conditions.map(_.map(_.delegate)).toList, default.delegate))

  /** Uses the `onSuccess` response description for 101 responses (switching protocols) on JVM/Native, 200 responses on
    * JS. Otherwise, use the `onError` description.
    */
  def asWebSocketEither[A, B](
      onError: ResponseAs[A],
      onSuccess: WebSocketResponseAs[Identity, B]
  ): WebSocketResponseAs[Identity, Either[A, B]] =
    SttpExtensions.asWebSocketEitherPlatform(onError, onSuccess)
}
