package sttp.client4

import sttp.monad.MonadError
import sttp.capabilities.{Effect, WebSockets}
import sttp.client4.monad.IdMonad

/** A specific implementation of HTTP request sending logic.
  *
  * The [[send]] method '''should not''' be used directly by client code, if possible. Instead, the [[Request.send]],
  * [[StreamRequest.send]], [[WebSocketRequest.send]] or [[WebSocketStreamRequest.send]] methods (depending on the type
  * of the request) should be used, providing a specific backend instance as a parameter.
  *
  * When creating an instance of a backend, one of the [[Backend]] traits should be mixed in, reflecting the effect type
  * and the `P` capabilities: [[Backend]], [[SyncBackend]], [[WebSocketBackend]], [[WebSocketSyncBackend]] [[StreamBackend]],
  * [[WebSocketStreamBackend]]. This is required in order to provide a better developer experience when sending
  * requests: the resulting type has less type parameters.
  *
  * @note
  *   Backends should try to classify known HTTP-related exceptions into one of the categories specified by
  *   [[SttpClientException]]. Other exceptions are thrown unchanged.
  * @tparam F
  *   The effect type used to represent side-effects, such as obtaining the response for a request. E.g. [[Identity]]
  *   for synchronous backends, [[scala.concurrent.Future]] for asynchronous backends.
  * @tparam P
  *   Capabilities supported by this backend, in addition to [[Effect]]. This might be `Any` (no special capabilities),
  *   subtype of [[sttp.capabilities.Streams]] (the ability to send and receive streaming bodies) or [[WebSockets]] (the
  *   ability to handle websocket requests).
  */
trait GenericBackend[F[_], +P] {

  /** Send the given request. Should only be used when implementing new backends, or backend wrappers. Client code
    * should instead use the `send` methods on the request type, e.g. [[Request.send]].
    */
  def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]]

  /** Close the backend, releasing any resources (such as thread or connection pools) that have been allocated when
    * opening or using the backend.
    */
  def close(): F[Unit]

  /** A monad instance for the `F` effect type. Allows writing wrapper backends, which `map`/`flatMap`` over the return
    * value of [[send]].
    */
  def monad: MonadError[F]
}

/** A [[GenericBackend]] which doesn't support any capabilities, and uses `F` to represent side-effects. */
trait Backend[F[_]] extends GenericBackend[F, Any]

/** A [[GenericBackend]] which is synchronous (side effects are run directly), and doesn't support any capabilities. */
trait SyncBackend extends Backend[Identity] {
  override def monad: MonadError[Identity] = IdMonad
}

/** A [[GenericBackend]] which is synchronous (side effects are run directly), and supports web sockets. */
trait WebSocketSyncBackend extends SyncBackend with WebSocketBackend[Identity] {
  override def monad: MonadError[Identity] = IdMonad
}

/** A [[GenericBackend]] which supports streams of type `S` and uses `F` to represent side-effects. */
trait StreamBackend[F[_], +S] extends Backend[F] with GenericBackend[F, S]

/** A [[GenericBackend]] which supports web sockets and uses `F` to represent side-effects. */
trait WebSocketBackend[F[_]] extends Backend[F] with GenericBackend[F, WebSockets]

/** A [[GenericBackend]] which supports websockets, streams of type `S` and uses `F` to represent side-effects. */
trait WebSocketStreamBackend[F[_], S]
    extends WebSocketBackend[F]
    with StreamBackend[F, S]
    with GenericBackend[F, S with WebSockets]
