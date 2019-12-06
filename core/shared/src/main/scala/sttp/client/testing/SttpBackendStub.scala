package sttp.client.testing

import java.io.InputStream

import sttp.client._
import sttp.client.internal._
import sttp.client.monad.{FutureMonad, IdMonad, MonadError}
import sttp.client.testing.SttpBackendStub._
import sttp.client.internal.SttpFile
import sttp.client.ws.WebSocketResponse
import sttp.client.{IgnoreResponse, ResponseAs, ResponseAsByteArray, SttpBackend}
import sttp.model.StatusCode

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
class SttpBackendStub[F[_], S] private (
    monad: MonadError[F],
    matchers: PartialFunction[Request[_, _], F[Response[_]]],
    fallback: Option[SttpBackend[F, S, NothingT]]
) extends SttpBackend[F, S, NothingT] { // TODO

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
  def whenRequestMatchesPartial(partial: PartialFunction[Request[_, _], Response[_]]): SttpBackendStub[F, S] = {
    val wrappedPartial = partial.andThen(monad.unit _)
    new SttpBackendStub(monad, matchers.orElse(wrappedPartial), fallback)
  }

  override def send[T](request: Request[T, S]): F[Response[T]] = {
    Try(matchers.lift(request)) match {
      case Success(Some(response)) =>
        tryAdjustResponseType(monad, request.response, response.asInstanceOf[F[Response[T]]])
      case Success(None) =>
        fallback match {
          case None =>
            val response = wrapResponse(
              Response[String](s"Not Found: ${request.uri}", StatusCode.NotFound, "Not Found", Nil, Nil)
            )
            tryAdjustResponseType(monad, request.response, response)
          case Some(fb) => fb.send(request)
        }
      case Failure(e) => monad.error(e)
    }
  }

  override def openWebsocket[T, WR](request: Request[T, S], handler: NothingT[WR]): F[WebSocketResponse[WR]] =
    handler // nothing is everything

  private def wrapResponse[T](r: Response[_]): F[Response[T]] =
    monad.unit(r.asInstanceOf[Response[T]])

  override def close(): F[Unit] = monad.unit(())

  override def responseMonad: MonadError[F] = monad

  class WhenRequest(p: Request[_, _] => Boolean) {
    def thenRespondOk(): SttpBackendStub[F, S] =
      thenRespondWithCode(StatusCode.Ok)
    def thenRespondNotFound(): SttpBackendStub[F, S] =
      thenRespondWithCode(StatusCode.NotFound, "Not found")
    def thenRespondServerError(): SttpBackendStub[F, S] =
      thenRespondWithCode(StatusCode.InternalServerError, "Internal server error")
    def thenRespondWithCode(status: StatusCode, msg: String = ""): SttpBackendStub[F, S] = {
      val body = if (status.isSuccess) Right(msg) else Left(msg)
      thenRespond(Response(body, status, msg))
    }
    def thenRespond[T](body: T): SttpBackendStub[F, S] =
      thenRespond(Response[T](body, StatusCode.Ok, "OK"))
    def thenRespond[T](resp: => Response[T]): SttpBackendStub[F, S] = {
      val m: PartialFunction[Request[_, _], F[Response[_]]] = {
        case r if p(r) => monad.eval(resp)
      }
      new SttpBackendStub(monad, matchers.orElse(m), fallback)
    }

    /**
      * Not thread-safe!
      */
    def thenRespondCyclic[T](bodies: T*): SttpBackendStub[F, S] = {
      thenRespondCyclicResponses(bodies.map(body => Response[T](body, StatusCode.Ok, "OK")): _*)
    }

    /**
      * Not thread-safe!
      */
    def thenRespondCyclicResponses[T](responses: Response[T]*): SttpBackendStub[F, S] = {
      val iterator = Iterator.continually(responses).flatten
      thenRespond(iterator.next)
    }
    def thenRespondWrapped(resp: => F[Response[_]]): SttpBackendStub[F, S] = {
      val m: PartialFunction[Request[_, _], F[Response[_]]] = {
        case r if p(r) => resp
      }
      new SttpBackendStub(monad, matchers.orElse(m), fallback)
    }
    def thenRespondWrapped(resp: Request[_, _] => F[Response[_]]): SttpBackendStub[F, S] = {
      val m: PartialFunction[Request[_, _], F[Response[_]]] = {
        case r if p(r) => resp(r)
      }
      new SttpBackendStub(monad, matchers.orElse(m), fallback)
    }
  }
}

object SttpBackendStub {

  /**
    * Create a stub synchronous backend (which doesn't wrap results in any
    * container), without streaming support.
    */
  def synchronous: SttpBackendStub[Identity, Nothing] =
    new SttpBackendStub[Identity, Nothing](IdMonad, PartialFunction.empty, None)

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
  def apply[F[_], S, S2 <: S](c: SttpBackend[F, S, NothingT]): SttpBackendStub[F, S2] =
    new SttpBackendStub[F, S2](c.responseMonad, PartialFunction.empty, None)

  /**
    * Create a stub backend using the given response monad (which determines
    * how requests are wrapped), and any stream type.
    */
  def apply[F[_], S](responseMonad: MonadError[F]): SttpBackendStub[F, S] =
    new SttpBackendStub[F, S](responseMonad, PartialFunction.empty, None)

  /**
    * Create a stub backend which delegates send requests to the given fallback
    * backend, if the request doesn't match any of the specified predicates.
    */
  def withFallback[F[_], S, S2 <: S](fallback: SttpBackend[F, S, NothingT]): SttpBackendStub[F, S2] =
    new SttpBackendStub[F, S2](fallback.responseMonad, PartialFunction.empty, Some(fallback))

  private[client] def tryAdjustResponseType[DesiredRType, RType, M[_]](
      monad: MonadError[M],
      ra: ResponseAs[DesiredRType, _],
      m: M[Response[RType]]
  ): M[Response[DesiredRType]] = {
    monad.map[Response[RType], Response[DesiredRType]](m) { r =>
      val newBody: Any = tryAdjustResponseBody(ra, r.body, r).getOrElse(r.body)
      r.copy(body = newBody.asInstanceOf[DesiredRType])
    }
  }

  private[client] def tryAdjustResponseBody[T, U](ra: ResponseAs[T, _], b: U, meta: ResponseMetadata): Option[T] = {
    ra match {
      case IgnoreResponse => Some(())
      case ResponseAsByteArray =>
        b match {
          case s: String       => Some(s.getBytes(Utf8))
          case a: Array[Byte]  => Some(a)
          case is: InputStream => Some(toByteArray(is))
          case _               => None
        }
      case ResponseAsStream() =>
        None
      case ResponseAsFile(_) =>
        b match {
          case f: SttpFile => Some(f)
          case _           => None
        }
      case MappedResponseAs(raw, g) =>
        tryAdjustResponseBody(raw, b, meta).map(g(_, meta))
      case ResponseAsFromMetadata(f) =>
        tryAdjustResponseBody(f(meta), b, meta)
    }
  }
}
