package sttp.client4

import sttp.capabilities.Streams
import sttp.model.StatusCode
import sttp.ws.WebSocketFrame

trait SttpWebSocketStreamApi {
  def asWebSocketStream[S](
      s: Streams[S]
  )(p: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): WebSocketStreamResponseAs[Either[String, Unit], S] =
    asWebSocketEither(asStringAlways, asWebSocketStreamAlways(s)(p))

  def asWebSocketStreamAlways[S](s: Streams[S])(
      p: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  ): WebSocketStreamResponseAs[Unit, S] = WebSocketStreamResponseAs[Unit, S](ResponseAsWebSocketStream(s, p))

  def fromMetadata[T, S](
      default: ResponseAs[T],
      conditions: ConditionalResponseAs[WebSocketStreamResponseAs[T, S]]*
  ): WebSocketStreamResponseAs[T, S] =
    WebSocketStreamResponseAs[T, S](
      ResponseAsFromMetadata(conditions.map(_.map(_.delegate)).toList, default.delegate)
    )

  /** Uses the `onSuccess` response specification for 101 responses (switching protocols), and the `onError`
    * specification otherwise.
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
