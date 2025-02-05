package sttp.client4.testing

import sttp.client4._
import sttp.monad.IdentityMonad
import sttp.shared.Identity

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
class SyncBackendStub(
    matchers: PartialFunction[GenericRequest[_, _], Response[StubBody]],
    fallback: Option[SyncBackend]
) extends AbstractBackendStub[Identity, Any](IdentityMonad, matchers, fallback)
    with SyncBackend {

  type Self = SyncBackendStub
  override protected def withMatchers(matchers: PartialFunction[GenericRequest[_, _], Response[StubBody]]) =
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
