package sttp.client3.testing

import sttp.client3.monad.IdMonad
import sttp.client3._
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import sttp.capabilities.WebSockets

/** A stub backend to use in tests.
  *
  * The stub can be configured to respond with a given response if the request matches a predicate (see the
  * [[whenRequestMatches()]] method).
  *
  * Note however, that this is not type-safe with respect to the type of the response body - the stub doesn't have a way
  * to check if the type of the body in the configured response is the same as the one specified by the request. Some
  * conversions will be attempted (e.g. from a `String` to a custom mapped type, as specified in the request, see the
  * documentation for more details).
  *
  * For web socket requests, the stub can be configured to returned both custom [[sttp.ws.WebSocket]] implementations,
  * as well as [[sttp.ws.testing.WebSocketStub]] instances.
  *
  * Predicates can match requests basing on the URI or headers. A [[ClassCastException]] might occur if for a given
  * request, a response is specified with the incorrect or inconvertible body type.
  */
class WebSocketBackendStub[F[_]](
                                  monad: MonadError[F],
                                  matchers: PartialFunction[GenericRequest[_, _], F[Response[_]]],
                                  fallback: Option[WebSocketBackend[F]]
) extends AbstractBackendStub[F, WebSockets](monad, matchers, fallback)
    with WebSocketBackend[F] {

  type Self = WebSocketBackendStub[F]
  override protected def withMatchers(
      matchers: PartialFunction[GenericRequest[_, _], F[Response[_]]]
  ): WebSocketBackendStub[F] =
    new WebSocketBackendStub(monad, matchers, fallback)
}

object WebSocketBackendStub {

  /** Create a stub of a synchronous backend (which doesn't use an effect type) */
  def synchronous: WebSocketBackendStub[Identity] = new WebSocketBackendStub(IdMonad, PartialFunction.empty, None)

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
    new WebSocketBackendStub[F](fallback.responseMonad, PartialFunction.empty, Some(fallback))
}
