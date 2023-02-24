package sttp.client3

import sttp.client3.internal.InternalResponseAs

/** Abstract representation of how a response body should be handled.
  *
  * @tparam T
  *   Target type as which the response will be read.
  * @tparam R
  * The backend capabilities required by the response description. This might be `Any` (no requirements), [[sttp.capabilities.Effect]]
  * (the backend must support the given effect type), [[sttp.capabilities.Streams]] (the ability to send and receive streaming bodies)
  * or [[sttp.capabilities.WebSockets]] (the ability to handle websocket requests).
  */
trait AbstractResponseAs[+T, -R] {
  private[client3] def internal: InternalResponseAs[T, R]
  def show: String = internal.show
}
