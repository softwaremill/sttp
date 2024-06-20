package sttp.client4.testing

import sttp.capabilities.WebSockets
import sttp.client4._
import sttp.monad.IdentityMonad
import sttp.shared.Identity

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
class WebSocketSyncBackendStub(
    matchers: PartialFunction[GenericRequest[_, _], Response[_]],
    fallback: Option[WebSocketSyncBackend]
) extends AbstractBackendStub[Identity, WebSockets](IdentityMonad, matchers, fallback)
    with WebSocketSyncBackend {

  type Self = WebSocketSyncBackendStub
  override protected def withMatchers(matchers: PartialFunction[GenericRequest[_, _], Response[_]]) =
    new WebSocketSyncBackendStub(matchers, fallback)
}

/** A stub of a synchronous backend. */
object WebSocketSyncBackendStub extends WebSocketSyncBackendStub(PartialFunction.empty, None) {

  /** Create a stub backend which delegates send requests to the given fallback backend, if the request doesn't match
    * any of the specified predicates.
    */
  def withFallback(fallback: WebSocketSyncBackend): WebSocketSyncBackendStub =
    new WebSocketSyncBackendStub(PartialFunction.empty, Some(fallback))
}
