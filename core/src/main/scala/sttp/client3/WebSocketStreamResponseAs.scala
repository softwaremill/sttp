package sttp.client3

import sttp.capabilities.WebSockets
import sttp.client3.internal.InternalResponseAs
import sttp.model.ResponseMetadata

/** Describes how the response of a [[WebSocketStreamRequest]] should be handled.
  *
  * The websocket response can be mapped over, to support custom types. The mapping can take into account the
  * [[ResponseMetadata]], that is the headers and status code. Responses can also be handled depending on the response
  * metadata.
  *
  * A number of `asWebSocket` helper methods are available as part of [[SttpApi]] and when importing `sttp.client3._`.
  *
  * @tparam T
  *   Target type as which the response will be read.
  */
class WebSocketStreamResponseAs[+T, S](private[client3] val internal: InternalResponseAs[T, S with WebSockets])
    extends AbstractResponseAs[T, S with WebSockets] {
  def map[T2](f: T => T2): WebSocketStreamResponseAs[T2, S] =
    new WebSocketStreamResponseAs[T2, S](internal.mapWithMetadata { case (t, _) => f(t) })
  def mapWithMetadata[T2](f: (T, ResponseMetadata) => T2): WebSocketStreamResponseAs[T2, S] =
    new WebSocketStreamResponseAs[T2, S](internal.mapWithMetadata(f))

  def show: String = internal.show
  def showAs(s: String): WebSocketStreamResponseAs[T, S] = new WebSocketStreamResponseAs[T, S](internal.showAs(s))
}
