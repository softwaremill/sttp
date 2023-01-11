package sttp.client3

import sttp.client3.internal.InternalResponseAs
import sttp.model.ResponseMetadata

/** Describes how the response body of a [[StreamRequest]] should be handled.
  *
  * The stream response can be mapped over, to support custom types. The mapping can take into account the
  * [[ResponseMetadata]], that is the headers and status code.
  *
  * A number of `asStream[Type]` helper methods are available as part of [[SttpApi]] and when importing
  * `sttp.client3._`.
  *
  * @tparam T
  *   Target type as which the response will be read.
  * @tparam S
  *   The type of stream, used to receive the response body bodies.
  */
class StreamResponseAs[+T, S](private[client3] val internal: InternalResponseAs[T, S])
    extends AbstractResponseAs[T, S] {
  def map[T2](f: T => T2): StreamResponseAs[T2, S] =
    new StreamResponseAs(internal.mapWithMetadata { case (t, _) => f(t) })
  def mapWithMetadata[T2](f: (T, ResponseMetadata) => T2): StreamResponseAs[T2, S] =
    new StreamResponseAs(internal.mapWithMetadata(f))

  def showAs(s: String): StreamResponseAs[T, S] = new StreamResponseAs(internal.showAs(s))
}
