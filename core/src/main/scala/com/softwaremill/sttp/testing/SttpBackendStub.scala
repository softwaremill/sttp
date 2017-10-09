package com.softwaremill.sttp.testing

import com.softwaremill.sttp.testing.SttpBackendStub._
import com.softwaremill.sttp.{MonadError, Request, Response, SttpBackend}

import scala.language.higherKinds

/**
  * A stub backend to use in tests.
  *
  * The stub can be configured to respond with a given response if the
  * request matches a predicate (see the [[whenRequestMatches()]] method).
  *
  * Note however, that this is not type-safe with respect to the type of the
  * response body - the stub doesn't have a way to check if the type of the
  * body in the configured response is the same as the one specified by the
  * request. Hence, the predicates can match requests basing on the URI
  * or headers. A [[ClassCastException]] might occur if for a given request,
  * a response is specified with the incorrect body type.
  */
class SttpBackendStub[R[_], S] private (rm: MonadError[R],
                                        matchers: Vector[Matcher[_]],
                                        fallback: Option[SttpBackend[R, S]])
    extends SttpBackend[R, S] {

  /**
    * Specify how the stub backend should respond to requests matching the
    * given predicate. Note that the stubs are immutable, and each new
    * specification that is added yields a new stub instance.
    */
  def whenRequestMatches(p: Request[_, _] => Boolean): WhenRequest =
    new WhenRequest(p)

  override def send[T](request: Request[T, S]): R[Response[T]] = {
    matchers
      .collectFirst {
        case matcher if matcher(request) => matcher.response
      } match {
      case Some(response) => wrapResponse(response)
      case None =>
        fallback match {
          case None     => wrapResponse(DefaultResponse)
          case Some(fb) => fb.send(request)
        }
    }
  }

  private def wrapResponse[T](r: Response[_]): R[Response[T]] =
    rm.unit(r.asInstanceOf[Response[T]])

  override def close(): Unit = {}

  override def responseMonad: MonadError[R] = rm

  class WhenRequest(p: Request[_, _] => Boolean) {
    def thenRespondOk(): SttpBackendStub[R, S] =
      thenRespondWithCode(200)
    def thenRespondNotFound(): SttpBackendStub[R, S] =
      thenRespondWithCode(404, "Not found")
    def thenRespondServerError(): SttpBackendStub[R, S] =
      thenRespondWithCode(500, "Internal server error")
    def thenRespondWithCode(code: Int,
                            msg: String = ""): SttpBackendStub[R, S] =
      thenRespond(Response[Nothing](Left(msg), code, Nil, Nil))
    def thenRespond[T](body: T): SttpBackendStub[R, S] =
      thenRespond(Response[T](Right(body), 200, Nil, Nil))
    def thenRespond[T](resp: Response[T]): SttpBackendStub[R, S] =
      new SttpBackendStub(rm, matchers :+ Matcher(p, resp), fallback)
  }
}

object SttpBackendStub {

  /**
    * Create a stub backend for testing, which uses the same response wrappers
    * and supports the same stream type as the given "real" backend.
    *
    * @tparam S2 This is a work-around for the problem described here:
    *            [[https://stackoverflow.com/questions/46642623/cannot-infer-contravariant-nothing-type-parameter]].
    */
  def apply[R[_], S, S2 <: S](c: SttpBackend[R, S]): SttpBackendStub[R, S2] =
    new SttpBackendStub[R, S2](c.responseMonad, Vector.empty, None)

  /**
    * Create a stub backend using the given response monad (which determines
    * how requests are wrapped), and any stream type.
    */
  def apply[R[_], S](responseMonad: MonadError[R]): SttpBackendStub[R, S] =
    new SttpBackendStub[R, S](responseMonad, Vector.empty, None)

  /**
    * Create a stub backend which delegates send requests to the given fallback
    * backend, if the request doesn't match any of the specified predicates.
    */
  def withFallback[R[_], S, S2 <: S](
      fallback: SttpBackend[R, S]): SttpBackendStub[R, S2] =
    new SttpBackendStub[R, S2](fallback.responseMonad,
                               Vector.empty,
                               Some(fallback))

  private val DefaultResponse =
    Response[Nothing](Left("Not Found"), 404, Nil, Nil)

  private case class Matcher[T](p: Request[T, _] => Boolean,
                                response: Response[T]) {
    def apply(request: Request[_, _]): Boolean =
      p(request.asInstanceOf[Request[T, _]])
  }
}
