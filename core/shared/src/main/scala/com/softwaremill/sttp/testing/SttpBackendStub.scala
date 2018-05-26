package com.softwaremill.sttp.testing

import java.io.InputStream

import com.softwaremill.sttp._
import com.softwaremill.sttp.internal.SttpFile
import com.softwaremill.sttp.testing.SttpBackendStub._

import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

/**
  * A stub backend to use in tests.
  *
  * The stub can be configured to respond with a given response if the
  * request matches a predicate (see the [[whenRequestMatches()]] method).
  *
  * Note however, that this is not type-safe with respect to the type of the
  * response body - the stub doesn't have a way to check if the type of the
  * body in the configured response is the same as the one specified by the
  * request. Some conversions will be attempted (e.g. from a `String` to
  * a custom mapped type, as specified in the request, see the documentation
  * for more details).
  *
  * Hence, the predicates can match requests basing on the URI
  * or headers. A [[ClassCastException]] might occur if for a given request,
  * a response is specified with the incorrect or inconvertible body type.
  */
class SttpBackendStub[R[_], S] private (rm: MonadError[R],
                                        matchers: PartialFunction[Request[_, _], R[Response[_]]],
                                        fallback: Option[SttpBackend[R, S]])
    extends SttpBackend[R, S] {

  /**
    * Specify how the stub backend should respond to requests matching the
    * given predicate.
    *
    * Note that the stubs are immutable, and each new
    * specification that is added yields a new stub instance.
    */
  def whenRequestMatches(p: Request[_, _] => Boolean): WhenRequest =
    new WhenRequest(p)

  /**
    * Specify how the stub backend should respond to any request (catch-all).
    *
    * Note that the stubs are immutable, and each new
    * specification that is added yields a new stub instance.
    */
  def whenAnyRequest: WhenRequest = whenRequestMatches(_ => true)

  /**
    * Specify how the stub backend should respond to requests using the
    * given partial function.
    *
    * Note that the stubs are immutable, and each new
    * specification that is added yields a new stub instance.
    */
  def whenRequestMatchesPartial(partial: PartialFunction[Request[_, _], Response[_]]): SttpBackendStub[R, S] = {
    val wrappedPartial = partial.andThen(rm.unit)
    new SttpBackendStub(rm, matchers.orElse(wrappedPartial), fallback)
  }

  override def send[T](request: Request[T, S]): R[Response[T]] = {
    Try(matchers.lift(request)) match {
      case Success(Some(response)) =>
        tryAdjustResponseType(rm, request.response, response.asInstanceOf[R[Response[T]]])
      case Success(None) =>
        fallback match {
          case None =>
            wrapResponse(Response[Nothing](Left("Not Found: " + request.uri), 404, "Not Found", Nil, Nil))
          case Some(fb) => fb.send(request)
        }
      case Failure(e) => rm.error(e)
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
    def thenRespondWithCode(code: Int, msg: String = ""): SttpBackendStub[R, S] = {
      val body = if (code >= 200 && code < 300) Right(msg) else Left(msg)
      thenRespond(Response(body, code, msg, Nil, Nil))
    }
    def thenRespond[T](body: T): SttpBackendStub[R, S] =
      thenRespond(Response[T](Right(body), 200, "OK", Nil, Nil))
    def thenRespond[T](resp: => Response[T]): SttpBackendStub[R, S] = {
      val m: PartialFunction[Request[_, _], R[Response[_]]] = {
        case r if p(r) => rm.unit(resp)
      }
      new SttpBackendStub(rm, matchers.orElse(m), fallback)
    }
    def thenRespondWrapped(resp: => R[Response[_]]): SttpBackendStub[R, S] = {
      val m: PartialFunction[Request[_, _], R[Response[_]]] = {
        case r if p(r) => resp
      }
      new SttpBackendStub(rm, matchers.orElse(m), fallback)
    }
    def thenRespondWrapped(resp: Request[_, _] => R[Response[_]]): SttpBackendStub[R, S] = {
      val m: PartialFunction[Request[_, _], R[Response[_]]] = {
        case r if p(r) => resp(r)
      }
      new SttpBackendStub(rm, matchers.orElse(m), fallback)
    }
  }
}

object SttpBackendStub {

  /**
    * Create a stub synchronous backend (which doesn't wrap results in any
    * container), without streaming support.
    */
  def synchronous: SttpBackendStub[Id, Nothing] =
    new SttpBackendStub[Id, Nothing](IdMonad, PartialFunction.empty, None)

  /**
    * Create a stub asynchronous backend (which wraps results in Scala's
    * built-in `Future`), without streaming support.
    */
  def asynchronousFuture: SttpBackendStub[Future, Nothing] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    new SttpBackendStub[Future, Nothing](new FutureMonad(), PartialFunction.empty, None)
  }

  /**
    * Create a stub backend for testing, which uses the same response wrappers
    * and supports the same stream type as the given "real" backend.
    *
    * @tparam S2 This is a work-around for the problem described here:
    *            [[https://stackoverflow.com/questions/46642623/cannot-infer-contravariant-nothing-type-parameter]].
    */
  def apply[R[_], S, S2 <: S](c: SttpBackend[R, S]): SttpBackendStub[R, S2] =
    new SttpBackendStub[R, S2](c.responseMonad, PartialFunction.empty, None)

  /**
    * Create a stub backend using the given response monad (which determines
    * how requests are wrapped), and any stream type.
    */
  def apply[R[_], S](responseMonad: MonadError[R]): SttpBackendStub[R, S] =
    new SttpBackendStub[R, S](responseMonad, PartialFunction.empty, None)

  /**
    * Create a stub backend which delegates send requests to the given fallback
    * backend, if the request doesn't match any of the specified predicates.
    */
  def withFallback[R[_], S, S2 <: S](fallback: SttpBackend[R, S]): SttpBackendStub[R, S2] =
    new SttpBackendStub[R, S2](fallback.responseMonad, PartialFunction.empty, Some(fallback))

  private[sttp] def tryAdjustResponseType[DesiredRType, RType, M[_]](
      rm: MonadError[M],
      ra: ResponseAs[DesiredRType, _],
      m: M[Response[RType]]): M[Response[DesiredRType]] = {
    rm.map[Response[RType], Response[DesiredRType]](m) { r =>
      r.body match {
        case Left(_) => r.asInstanceOf[Response[DesiredRType]]
        case Right(body) =>
          val newBody: Any = tryAdjustResponseBody(ra, body).getOrElse(body)
          r.copy(body = Right[String, DesiredRType](newBody.asInstanceOf[DesiredRType]))
      }
    }
  }

  private[sttp] def tryAdjustResponseBody[T, U](ra: ResponseAs[T, _], b: U): Option[T] = {
    ra match {
      case IgnoreResponse => Some(())
      case ResponseAsString(enc) =>
        b match {
          case s: String       => Some(s)
          case a: Array[Byte]  => Some(new String(a, enc))
          case is: InputStream => Some(new String(toByteArray(is), enc))
          case _               => None
        }
      case ResponseAsByteArray =>
        b match {
          case s: String       => Some(s.getBytes(Utf8))
          case a: Array[Byte]  => Some(a)
          case is: InputStream => Some(toByteArray(is))
          case _               => None
        }
      case ras @ ResponseAsStream() =>
        None
      case ResponseAsFile(file, overwrite) =>
        b match {
          case f: SttpFile => Some(f)
          case _       => None
        }
      case MappedResponseAs(raw, g) =>
        tryAdjustResponseBody(raw, b).map(g)
    }
  }
}
