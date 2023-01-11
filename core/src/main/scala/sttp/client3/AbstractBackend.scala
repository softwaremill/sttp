package sttp.client3

import sttp.monad.MonadError
import sttp.capabilities.Effect

/** The common ancestor of all sttp backends.
  *
  * An [[AbstractBackend]] cannot be used directly as it does not contain any public method to send a request. You
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
trait AbstractBackend[F[_], +P] {

  def internalSend[T](request: AbstractRequest[T, P with Effect[F]]): F[Response[T]]

  def close(): F[Unit]

  /** A monad instance for the effect type used when returning responses. Allows writing wrapper backends, which
    * map/flatMap over the return value of [[send]].
    */
  def responseMonad: MonadError[F]
}
