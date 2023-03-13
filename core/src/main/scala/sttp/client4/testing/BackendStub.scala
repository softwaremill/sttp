package sttp.client4.testing

import sttp.client4._
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

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
  * Predicates can match requests basing on the URI or headers. A [[ClassCastException]] might occur if for a given
  * request, a response is specified with the incorrect or inconvertible body type.
  */

class BackendStub[F[_]](
    monad: MonadError[F],
    matchers: PartialFunction[GenericRequest[_, _], F[Response[_]]],
    fallback: Option[Backend[F]]
) extends AbstractBackendStub[F, Any](monad, matchers, fallback)
    with Backend[F] {

  type Self = BackendStub[F]
  override protected def withMatchers(matchers: PartialFunction[GenericRequest[_, _], F[Response[_]]]) =
    new BackendStub(monad, matchers, fallback)
}

object BackendStub {

  /** Create a stub of a synchronous backend. */
  def synchronous: SyncBackendStub = new SyncBackendStub(PartialFunction.empty, None)

  /** Create a stub of an asynchronous backend (which uses the Scala's built-in [[Future]] as the effect type). */
  def asynchronousFuture(implicit ec: ExecutionContext): BackendStub[Future] =
    new BackendStub(new FutureMonad(), PartialFunction.empty, None)

  /** Create a stub backend using the given response monad (which determines the effect type for responses). */
  def apply[F[_]](responseMonad: MonadError[F]): BackendStub[F] =
    new BackendStub[F](responseMonad, PartialFunction.empty, None)

  /** Create a stub backend which delegates send requests to the given fallback backend, if the request doesn't match
    * any of the specified predicates.
    */
  def withFallback[F[_]](fallback: Backend[F]): BackendStub[F] =
    new BackendStub[F](fallback.monad, PartialFunction.empty, Some(fallback))
}
