package sttp.client4.testing

import sttp.client4._
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

/** A stub backend to use in tests.
  *
  * The stub can be configured to respond with a given response if the request matches a predicate (see the `when...`
  * methods).
  *
  * The response bodies can be adjusted to what's described in the request description, or returned exactly as provided.
  * See [[StubBody]] for details on how the body is adjusted, and [[ResponseStub]] for convenience methods to create
  * responses to be used in tests. The `.thenRespondAdjust` and `.thenRespondExact` methods cover the common use-cases.
  *
  * Note that providing the stub body is not type-safe: the stub doesn't have a way to check if the type of the body in
  * the configured response is the same as, or can be converted to, the one specified by the request; hence, a
  * [[ClassCastException]] or [[IllegalArgumentException]] might occur, while sending requests using the stub backend.
  *
  * Predicates can match requests basing on the URI or headers.
  */
class BackendStub[F[_]](
    monad: MonadError[F],
    matchers: PartialFunction[GenericRequest[_, _], F[Response[StubBody]]],
    fallback: Option[Backend[F]]
) extends AbstractBackendStub[F, Any](monad, matchers, fallback)
    with Backend[F] {

  type Self = BackendStub[F]
  override protected def withMatchers(matchers: PartialFunction[GenericRequest[_, _], F[Response[StubBody]]]) =
    new BackendStub(monad, matchers, fallback)
}

object BackendStub {

  /** Create a stub of a synchronous backend. */
  def synchronous: SyncBackendStub = new SyncBackendStub(PartialFunction.empty, None)

  /** Create a stub of an asynchronous backend (which uses the Scala's built-in [[Future]] as the effect type). */
  def asynchronousFuture(implicit ec: ExecutionContext): BackendStub[Future] =
    new BackendStub(new FutureMonad(), PartialFunction.empty, None)

  /** Create a stub backend using the given response monad (which determines the effect type for responses). */
  def apply[F[_]](monad: MonadError[F]): BackendStub[F] =
    new BackendStub[F](monad, PartialFunction.empty, None)

  /** Create a stub backend which delegates send requests to the given fallback backend, if the request doesn't match
    * any of the specified predicates.
    */
  def withFallback[F[_]](fallback: Backend[F]): BackendStub[F] =
    new BackendStub[F](fallback.monad, PartialFunction.empty, Some(fallback))
}
