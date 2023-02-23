package sttp.client3.testing

import sttp.client3.monad.IdMonad
import sttp.client3._
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
  * For requests which return the response as a stream, if the stub should return a raw stream value (which should then
  * be passed to the stream-consuming function, or mapped to another value), it should be wrapped with [[RawStream]].
  *
  * Predicates can match requests basing on the URI or headers. A [[ClassCastException]] might occur if for a given
  * request, a response is specified with the incorrect or inconvertible body type.
  */
class StreamBackendStub[F[_], S](
                                  monad: MonadError[F],
                                  matchers: PartialFunction[GenericRequest[_, _], F[Response[_]]],
                                  fallback: Option[StreamBackend[F, S]]
) extends AbstractBackendStub[F, S](monad, matchers, fallback)
    with StreamBackend[F, S] {

  type Self = StreamBackendStub[F, S]
  override protected def withMatchers(
      matchers: PartialFunction[GenericRequest[_, _], F[Response[_]]]
  ): StreamBackendStub[F, S] =
    new StreamBackendStub(monad, matchers, fallback)
}

object StreamBackendStub {

  /** Create a stub of a synchronous backend. */
  def synchronous[S]: StreamBackendStub[Identity, S] = new StreamBackendStub(IdMonad, PartialFunction.empty, None)

  /** Create a stub of an asynchronous backend (which uses the Scala's built-in [[Future]] as the effect type). */
  def asynchronousFuture[S](implicit ec: ExecutionContext): StreamBackendStub[Future, S] =
    new StreamBackendStub(new FutureMonad(), PartialFunction.empty, None)

  /** Create a stub backend using the given response monad (which determines the effect type for responses). */
  def apply[F[_], S](monad: MonadError[F]): StreamBackendStub[F, S] =
    new StreamBackendStub(monad, PartialFunction.empty, None)

  /** Create a stub backend which delegates send requests to the given fallback backend, if the request doesn't match
    * any of the specified predicates.
    */
  def withFallback[F[_], S](fallback: StreamBackend[F, S]): StreamBackendStub[F, S] =
    new StreamBackendStub(fallback.responseMonad, PartialFunction.empty, Some(fallback))
}
