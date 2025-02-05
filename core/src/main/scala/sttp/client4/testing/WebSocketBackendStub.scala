package sttp.client4.testing

import sttp.client4._
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import sttp.capabilities.WebSockets

/** A stub backend to use in tests.
  *
  * The stub can be configured to respond with a given response if the request matches a predicate (see the `when...`
  * methods).
  *
  * The response bodies can be adjusted to what's described in the request description, or returned exactly as provided.
  * See [[StubBody]] for details on how the body is adjusted, and [[ResponseStub]] for convenience methods to create
  * responses to be used in tests. The `.thenRespondAdjust` and `.thenRespondExact` methods cover the common use-cases.
  *
  * For web socket requests, the stub can be configured to returned both custom [[sttp.ws.WebSocket]] implementations,
  * as well as [[sttp.ws.testing.WebSocketStub]] instances.
  *
  * Note that providing the stub body is not type-safe: the stub doesn't have a way to check if the type of the body in
  * the configured response is the same as, or can be converted to, the one specified by the request; hence, a
  * [[ClassCastException]] or [[IllegalArgumentException]] might occur, while sending requests using the stub backend.
  *
  * Predicates can match requests basing on the URI or headers.
  */
class WebSocketBackendStub[F[_]](
    monad: MonadError[F],
    matchers: PartialFunction[GenericRequest[_, _], F[Response[StubBody]]],
    fallback: Option[WebSocketBackend[F]]
) extends AbstractBackendStub[F, WebSockets](monad, matchers, fallback)
    with WebSocketBackend[F] {

  type Self = WebSocketBackendStub[F]
  override protected def withMatchers(
      matchers: PartialFunction[GenericRequest[_, _], F[Response[StubBody]]]
  ): WebSocketBackendStub[F] =
    new WebSocketBackendStub(monad, matchers, fallback)
}

object WebSocketBackendStub {

  /** Create a stub of a synchronous backend (which doesn't use an effect type) */
  def synchronous: WebSocketSyncBackendStub = WebSocketSyncBackendStub

  /** Create a stub of an asynchronous backend (which uses the Scala's built-in [[Future]] as the effect type). */
  def asynchronousFuture(implicit ec: ExecutionContext): WebSocketBackendStub[Future] =
    new WebSocketBackendStub(new FutureMonad(), PartialFunction.empty, None)

  /** Create a stub backend using the given response monad (which determines the effect type for responses). */
  def apply[F[_]](monad: MonadError[F]): WebSocketBackendStub[F] =
    new WebSocketBackendStub(monad, PartialFunction.empty, None)

  /** Create a stub backend which delegates send requests to the given fallback backend, if the request doesn't match
    * any of the specified predicates.
    */
  def withFallback[F[_]](fallback: WebSocketBackend[F]): WebSocketBackendStub[F] =
    new WebSocketBackendStub[F](fallback.monad, PartialFunction.empty, Some(fallback))
}
