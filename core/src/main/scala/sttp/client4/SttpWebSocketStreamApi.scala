package sttp.client4

import sttp.capabilities.Streams
import sttp.model.StatusCode
import sttp.ws.WebSocketFrame

trait SttpWebSocketStreamApi {

  /** Handles the response body by either reading a string (for non-2xx responses), or otherwise using the given `p`
    * stream processing pipe to handle the incoming & produce the outgoing web socket frames.
    *
    * The web socket is always closed after `p` completes.
    *
    * A non-blocking, asynchronous streaming implementation must be provided as the [[Streams]] parameter.
    */
  def asWebSocketStream[S](
      s: Streams[S]
  )(p: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): WebSocketStreamResponseAs[Either[String, Unit], S] =
    asWebSocketEither(asStringAlways, asWebSocketStreamAlways(s)(p))

  /** Handles the response as a web socket, using the given `p` stream processing pipe to handle the incoming & produce
    * the outgoing web socket frames, if the status code is 2xx. Otherwise, returns a failed effect (with
    * [[HttpError]]).
    *
    * The effect type used by `f` must be compatible with the effect type of the backend. The web socket is always
    * closed after `p` completes.
    *
    * A non-blocking, asynchronous streaming implementation must be provided as the [[Streams]] parameter.
    *
    * @see
    *   the [[ResponseAs.orFail]] method can be used to convert any response description which returns an `Either` into
    *   an exception-throwing variant.
    */
  def asWebSocketStreamOrFail[S](
      s: Streams[S]
  )(p: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): WebSocketStreamResponseAs[Unit, S] =
    asWebSocketStream(s)(p).orFail

  /** Handles the response body by using the given `p` stream processing pipe to handle the incoming & produce the
    * outgoing web socket frames, regardless of the status code.
    *
    * The web socket is always closed after `p` completes.
    *
    * A non-blocking, asynchronous streaming implementation must be provided as the [[Streams]] parameter.
    */
  def asWebSocketStreamAlways[S](s: Streams[S])(
      p: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  ): WebSocketStreamResponseAs[Unit, S] = WebSocketStreamResponseAs[Unit, S](ResponseAsWebSocketStream(s, p))

  /** Uses the [[ResponseAs]] description that matches the condition (using the response's metadata).
    *
    * This allows using different response description basing on the status code, for example. If none of the conditions
    * match, the default response handling description is used.
    */
  def fromMetadata[T, S](
      default: ResponseAs[T],
      conditions: ConditionalResponseAs[WebSocketStreamResponseAs[T, S]]*
  ): WebSocketStreamResponseAs[T, S] =
    WebSocketStreamResponseAs[T, S](
      ResponseAsFromMetadata(conditions.map(_.map(_.delegate)).toList, default.delegate)
    )

  /** Uses the `onSuccess` response description for 101 responses (switching protocols) on JVM/Native, 200 responses on
    * JS. Otherwise, use the `onError` description.
    */
  def asWebSocketEither[A, B, S](
      onError: ResponseAs[A],
      onSuccess: WebSocketStreamResponseAs[B, S]
  ): WebSocketStreamResponseAs[Either[A, B], S] =
    fromMetadata(
      onError.map(Left(_)),
      ConditionalResponseAs(_.code == StatusCode.SwitchingProtocols, onSuccess.map(Right(_)))
    ).showAs(s"either(${onError.show}, ${onSuccess.show})")

}
