package sttp.client3

import sttp.capabilities._
import sttp.monad.MonadError

/** A backend is used to send HTTP requests described by [[RequestT]]. Backends might wrap Java or Scala HTTP clients,
  * or other backends.
  *
  * @note
  *   Backends should try to classify exceptions into one of the categories specified by [[SttpClientException]]. Other
  *   exceptions should be thrown unchanged.
  * @tparam F
  *   The effect type used when returning responses. E.g. [[Identity]] for synchronous backends,
  *   [[scala.concurrent.Future]] for asynchronous backends.
  * @tparam P
  *   Capabilities supported by this backend, in addition to [[Effect]]. This might be `Any` (no special capabilities),
  *   [[Streams]] (the ability to send and receive streaming bodies) or [[WebSockets]] (the ability to handle websocket
  *   requests).
  */
trait SttpBackend[F[_], +P] {

  /** @tparam R
    *   The capabilities required by the request. This must be a subset of the the capabilities supported by the backend
    *   (which always includes `Effect[F]`).
    */
  def send[T, R >: P with Effect[F]](request: Request[T, R]): F[Response[T]]

  def close(): F[Unit]

  /** A monad instance for the effect type used when returning responses. Allows writing wrapper backends, which
    * map/flatMap over the return value of [[send]].
    */
  def responseMonad: MonadError[F]
}
