package sttp.client3.testing

import java.io.InputStream
import sttp.capabilities.{Effect, WebSockets}
import sttp.client3.internal.{SttpFile, _}
import sttp.client3.monad.IdMonad
import sttp.client3.testing.SttpBackendStub._
import sttp.client3.{IgnoreResponse, ResponseAs, ResponseAsByteArray, SttpBackend, _}
import sttp.model.{ResponseMetadata, StatusCode}
import sttp.monad.{FutureMonad, MonadError}
import sttp.monad.syntax._
import sttp.ws.WebSocket
import sttp.ws.testing.WebSocketStub

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/** A stub backend to use in tests.
  *
  * The stub can be configured to respond with a given response if the request matches a predicate (see the
  * [[whenRequestMatches()]] method).
  *
  * Note however, that this is not type-safe with respect to the type of the response body - the stub doesn't have a
  * way to check if the type of the body in the configured response is the same as the one specified by the
  * request. Some conversions will be attempted (e.g. from a `String` to a custom mapped type, as specified in the
  * request, see the documentation for more details).
  *
  * For web socket requests, the stub can be configured to returned both custom [[WebSocket]] implementations,
  * as well as [[WebSocketStub]] instances.
  *
  * For requests which return the response as a stream, if the stub should return a raw stream value (which should then
  * be passed to the stream-consuming function, or mapped to another value), it should be wrapped with [[RawStream]].
  *
  * Predicates can match requests basing on the URI or headers. A [[ClassCastException]] might occur if for a given
  * request, a response is specified with the incorrect or inconvertible body type.
  */
class SttpBackendStub[F[_], +P](
    monad: MonadError[F],
    matchers: PartialFunction[Request[_, _], F[Response[_]]],
    fallback: Option[SttpBackend[F, P]]
) extends SttpBackend[F, P] {

  /** Specify how the stub backend should respond to requests matching the
    * given predicate.
    *
    * Note that the stubs are immutable, and each new
    * specification that is added yields a new stub instance.
    */
  def whenRequestMatches(p: Request[_, _] => Boolean): WhenRequest =
    new WhenRequest(p)

  /** Specify how the stub backend should respond to any request (catch-all).
    *
    * Note that the stubs are immutable, and each new
    * specification that is added yields a new stub instance.
    */
  def whenAnyRequest: WhenRequest = whenRequestMatches(_ => true)

  /** Specify how the stub backend should respond to requests using the
    * given partial function.
    *
    * Note that the stubs are immutable, and each new
    * specification that is added yields a new stub instance.
    */
  def whenRequestMatchesPartial(
      partial: PartialFunction[Request[_, _], Response[_]]
  ): SttpBackendStub[F, P] = {
    val wrappedPartial: PartialFunction[Request[_, _], F[Response[_]]] =
      partial.andThen((r: Response[_]) => monad.unit(r))
    new SttpBackendStub[F, P](monad, matchers.orElse(wrappedPartial), fallback)
  }

  override def send[T, R >: P with Effect[F]](request: Request[T, R]): F[Response[T]] = {
    Try(matchers.lift(request)) match {
      case Success(Some(response)) =>
        tryAdjustResponseType(request.response, response.asInstanceOf[F[Response[T]]])(monad)
      case Success(None) =>
        fallback match {
          case None     => monad.error(new IllegalArgumentException(s"No behavior stubbed for request: $request"))
          case Some(fb) => fb.send(request)
        }
      case Failure(e) => monad.error(e)
    }
  }

  override def close(): F[Unit] = monad.unit(())

  override def responseMonad: MonadError[F] = monad

  class WhenRequest(p: Request[_, _] => Boolean) {
    def thenRespondOk(): SttpBackendStub[F, P] =
      thenRespondWithCode(StatusCode.Ok, "OK")
    def thenRespondNotFound(): SttpBackendStub[F, P] =
      thenRespondWithCode(StatusCode.NotFound, "Not found")
    def thenRespondServerError(): SttpBackendStub[F, P] =
      thenRespondWithCode(StatusCode.InternalServerError, "Internal server error")
    def thenRespondWithCode(status: StatusCode, msg: String = ""): SttpBackendStub[F, P] = {
      thenRespond(Response(msg, status, msg))
    }
    def thenRespond[T](body: T): SttpBackendStub[F, P] =
      thenRespond(Response[T](body, StatusCode.Ok, "OK"))
    def thenRespond[T](body: T, statusCode: StatusCode): SttpBackendStub[F, P] =
      thenRespond(Response[T](body, statusCode))
    def thenRespond[T](resp: => Response[T]): SttpBackendStub[F, P] = {
      val m: PartialFunction[Request[_, _], F[Response[_]]] = {
        case r if p(r) => monad.eval(resp)
      }
      new SttpBackendStub[F, P](monad, matchers.orElse(m), fallback)
    }

    def thenRespondCyclic[T](bodies: T*): SttpBackendStub[F, P] = {
      thenRespondCyclicResponses(bodies.map(body => Response[T](body, StatusCode.Ok, "OK")): _*)
    }

    def thenRespondCyclicResponses[T](responses: Response[T]*): SttpBackendStub[F, P] = {
      val iterator = AtomicCyclicIterator.unsafeFrom(responses)
      thenRespond(iterator.next())
    }

    def thenRespondF(resp: => F[Response[_]]): SttpBackendStub[F, P] = {
      val m: PartialFunction[Request[_, _], F[Response[_]]] = {
        case r if p(r) => resp
      }
      new SttpBackendStub[F, P](monad, matchers.orElse(m), fallback)
    }
    def thenRespondF(resp: Request[_, _] => F[Response[_]]): SttpBackendStub[F, P] = {
      val m: PartialFunction[Request[_, _], F[Response[_]]] = {
        case r if p(r) => resp(r)
      }
      new SttpBackendStub[F, P](monad, matchers.orElse(m), fallback)
    }
  }
}

object SttpBackendStub {

  /** Create a stub of a synchronous backend (which doesn't use an effect type), without streaming.
    */
  def synchronous: SttpBackendStub[Identity, WebSockets] =
    new SttpBackendStub(
      IdMonad,
      PartialFunction.empty,
      None
    )

  /** Create a stub of an asynchronous backend (which uses the Scala's built-in [[Future]] as the effect type),
    * without streaming.
    */
  def asynchronousFuture: SttpBackendStub[Future, WebSockets] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    new SttpBackendStub(
      new FutureMonad(),
      PartialFunction.empty,
      None
    )
  }

  /** Create a stub backend using the given response monad (which determines the effect type for responses),
    * and any capabilities (such as streaming or web socket support).
    */
  def apply[F[_], P](responseMonad: MonadError[F]): SttpBackendStub[F, P] =
    new SttpBackendStub[F, P](
      responseMonad,
      PartialFunction.empty,
      None
    )

  /** Create a stub backend which delegates send requests to the given fallback backend, if the request doesn't match
    * any of the specified predicates.
    */
  def withFallback[F[_], P0, P1 >: P0](
      fallback: SttpBackend[F, P0]
  ): SttpBackendStub[F, P1] =
    new SttpBackendStub[F, P1](
      fallback.responseMonad,
      PartialFunction.empty,
      Some(fallback)
    )

  private[client3] def tryAdjustResponseType[DesiredRType, RType, F[_]](
      ra: ResponseAs[DesiredRType, _],
      m: F[Response[RType]]
  )(implicit monad: MonadError[F]): F[Response[DesiredRType]] = {
    monad.flatMap[Response[RType], Response[DesiredRType]](m) { r =>
      tryAdjustResponseBody(ra, r.body, r).getOrElse(monad.unit(r.body)).map { nb =>
        r.copy(body = nb.asInstanceOf[DesiredRType])
      }
    }
  }

  private[client3] def tryAdjustResponseBody[F[_], T, U](
      ra: ResponseAs[T, _],
      b: U,
      meta: ResponseMetadata
  )(implicit monad: MonadError[F]): Option[F[T]] = {
    ra match {
      case IgnoreResponse => Some(().unit.asInstanceOf[F[T]])
      case ResponseAsByteArray =>
        b match {
          case s: String       => Some(s.getBytes(Utf8).unit.asInstanceOf[F[T]])
          case a: Array[Byte]  => Some(a.unit.asInstanceOf[F[T]])
          case is: InputStream => Some(toByteArray(is).unit.asInstanceOf[F[T]])
          case _               => None
        }
      case ResponseAsStream(_, f) =>
        b match {
          case RawStream(s) => Some(f.asInstanceOf[(Any, ResponseMetadata) => F[T]](s, meta))
          case _            => None
        }
      case ResponseAsStreamUnsafe(_) =>
        b match {
          case RawStream(s) => Some(s.unit.asInstanceOf[F[T]])
          case _            => None
        }
      case ResponseAsFile(_) =>
        b match {
          case f: SttpFile => Some(f.unit.asInstanceOf[F[T]])
          case _           => None
        }
      case ResponseAsWebSocket(f) =>
        b match {
          case wss: WebSocketStub[_] =>
            Some(f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[T]](wss.build[F](monad), meta))
          case ws: WebSocket[_] =>
            Some(f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[T]](ws.asInstanceOf[WebSocket[F]], meta))
          case _ => None
        }
      case ResponseAsWebSocketUnsafe() =>
        b match {
          case wss: WebSocketStub[_] => Some(wss.build[F](monad).unit.asInstanceOf[F[T]])
          case _                     => None
        }
      case ResponseAsWebSocketStream(_, _)   => None
      case MappedResponseAs(raw, g, _)       => tryAdjustResponseBody(raw, b, meta).map(_.map(g(_, meta)))
      case rfm: ResponseAsFromMetadata[_, _] => tryAdjustResponseBody(rfm(meta), b, meta)
      case ResponseAsBoth(l, r) =>
        tryAdjustResponseBody(l, b, meta).map { lAdjusted =>
          tryAdjustResponseBody(r, b, meta) match {
            case None            => lAdjusted.map((_, None))
            case Some(rAdjusted) => lAdjusted.flatMap(lResult => rAdjusted.map(rResult => (lResult, Some(rResult))))
          }
        }
    }
  }

  case class RawStream[T](s: T)
}
