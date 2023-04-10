package sttp.client4.testing

import sttp.client4._
import sttp.client4.monad.IdMonad

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
class SyncBackendStub(
    matchers: PartialFunction[GenericRequest[_, _], Response[_]],
    fallback: Option[SyncBackend]
) extends AbstractBackendStub[Identity, Any](IdMonad, matchers, fallback)
    with SyncBackend {

  type Self = SyncBackendStub
  override protected def withMatchers(matchers: PartialFunction[GenericRequest[_, _], Response[_]]) =
    new SyncBackendStub(matchers, fallback)
}

/** A stub of a synchronous backend. */
object SyncBackendStub extends SyncBackendStub(PartialFunction.empty, None) {

  /** Create a stub backend which delegates send requests to the given fallback backend, if the request doesn't match
    * any of the specified predicates.
    */
  def withFallback(fallback: SyncBackend): SyncBackendStub =
    new SyncBackendStub(PartialFunction.empty, Some(fallback))
}
