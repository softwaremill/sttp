package sttp.client3

import sttp.capabilities.Effect
import sttp.capabilities.WebSockets
import sttp.client3.internal.InternalResponseAs
import sttp.model.ResponseMetadata

/** Describes how the response of a [[WebSocketRequest]] should be handled.
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
class WebSocketResponseAs[F[_], +T](private[client3] val internal: InternalResponseAs[T, Effect[F] with WebSockets])
    extends AbstractResponseAs[T, Effect[F] with WebSockets] {
  def map[T2](f: T => T2): WebSocketResponseAs[F, T2] =
    new WebSocketResponseAs(internal.mapWithMetadata { case (t, _) => f(t) })
  def mapWithMetadata[T2](f: (T, ResponseMetadata) => T2): WebSocketResponseAs[F, T2] =
    new WebSocketResponseAs(internal.mapWithMetadata(f))

  def show: String = internal.show
  def showAs(s: String): WebSocketResponseAs[F, T] = new WebSocketResponseAs(internal.showAs(s))
}
