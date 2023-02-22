package sttp.client3

import sttp.monad.MonadError
import sttp.capabilities.{Effect, WebSockets}
import sttp.client3.monad.IdMonad

/** The common ancestor of all sttp backends.
  *
  * An [[GenericBackend]] cannot be used directly as it does not contain any public method to send a request. You
  * should use an instance of [[SyncBackend]], [[Backend]], [[WebSocketBackend]], [[StreamBackend]] or
  * [[WebSocketStreamBackend]] instead.
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
trait GenericBackend[F[_], +P] {

  def send[T](request: AbstractRequest[T, P with Effect[F]]): F[Response[T]]

  def close(): F[Unit]

  /** A monad instance for the effect type used when returning responses. Allows writing wrapper backends, which
    * map/flatMap over the return value of [[send]].
    */
  def responseMonad: MonadError[F]
}

trait Backend[F[_]] extends GenericBackend[F, Any]

trait SyncBackend extends Backend[Identity] {
  override def responseMonad: MonadError[Identity] = IdMonad
}

trait StreamBackend[F[_], +S] extends Backend[F] with GenericBackend[F, S]

trait WebSocketBackend[F[_]] extends Backend[F] with GenericBackend[F, WebSockets]

trait WebSocketStreamBackend[F[_], S]
    extends WebSocketBackend[F]
    with StreamBackend[F, S]
    with GenericBackend[F, S with WebSockets]
