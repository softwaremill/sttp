package sttp.client4.testing

import java.io.InputStream
import sttp.capabilities.Effect
import sttp.client4.internal._
import sttp.client4.testing.AbstractBackendStub._
import sttp.client4._
import sttp.model.{ResponseMetadata, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.WebSocket
import sttp.ws.testing.WebSocketStub

import scala.util.{Failure, Success, Try}

abstract class AbstractBackendStub[F[_], P](
                                             monad: MonadError[F],
                                             matchers: PartialFunction[GenericRequest[_, _], F[Response[_]]],
                                             fallback: Option[GenericBackend[F, P]]
) extends GenericBackend[F, P] {

  type Self

  protected def withMatchers(matchers: PartialFunction[GenericRequest[_, _], F[Response[_]]]): Self

  override def responseMonad: MonadError[F] = monad

  /** Specify how the stub backend should respond to requests matching the given predicate.
    *
    * Note that the stubs are immutable, and each new specification that is added yields a new stub instance.
    */
  def whenRequestMatches(p: GenericRequest[_, _] => Boolean): WhenRequest =
    new WhenRequest(p)

  /** Specify how the stub backend should respond to any request (catch-all).
    *
    * Note that the stubs are immutable, and each new specification that is added yields a new stub instance.
    */
  def whenAnyRequest: WhenRequest = whenRequestMatches(_ => true)

  /** Specify how the stub backend should respond to requests using the given partial function.
    *
    * Note that the stubs are immutable, and each new specification that is added yields a new stub instance.
    */
  def whenRequestMatchesPartial(partial: PartialFunction[GenericRequest[_, _], Response[_]]): Self = {
    val wrappedPartial: PartialFunction[GenericRequest[_, _], F[Response[_]]] =
      partial.andThen((r: Response[_]) => monad.unit(r))
    withMatchers(matchers.orElse(wrappedPartial))
  }

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] = {
    Try(matchers.lift(request)) match {
      case Success(Some(response)) =>
        adjustExceptions(request)(tryAdjustResponseType(request.response, response.asInstanceOf[F[Response[T]]])(monad))
      case Success(None) =>
        fallback match {
          case None     => monad.error(new IllegalArgumentException(s"No behavior stubbed for request: $request"))
          case Some(fb) => fb.send(request)
        }
      case Failure(e) => adjustExceptions(request)(monad.error(e))
    }
  }

  private def adjustExceptions[T](request: GenericRequest[_, _])(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(
      SttpClientException.defaultExceptionToSttpClientException(request, _)
    )

  override def close(): F[Unit] = monad.unit(())

  class WhenRequest(p: GenericRequest[_, _] => Boolean) {
    def thenRespondOk(): Self = thenRespondWithCode(StatusCode.Ok, "OK")
    def thenRespondNotFound(): Self = thenRespondWithCode(StatusCode.NotFound, "Not found")
    def thenRespondServerError(): Self = thenRespondWithCode(StatusCode.InternalServerError, "Internal server error")
    def thenRespondWithCode(status: StatusCode, msg: String = ""): Self = thenRespond(Response(msg, status, msg))
    def thenRespond[T](body: T): Self = thenRespond(Response[T](body, StatusCode.Ok, "OK"))
    def thenRespond[T](body: T, statusCode: StatusCode): Self = thenRespond(Response[T](body, statusCode))
    def thenRespond[T](resp: => Response[T]): Self = {
      val m: PartialFunction[GenericRequest[_, _], F[Response[_]]] = {
        case r if p(r) => monad.eval(resp)
      }
      withMatchers(matchers.orElse(m))
    }

    def thenRespondCyclic[T](bodies: T*): Self =
      thenRespondCyclicResponses(bodies.map(body => Response[T](body, StatusCode.Ok, "OK")): _*)

    def thenRespondCyclicResponses[T](responses: Response[T]*): Self = {
      val iterator = AtomicCyclicIterator.unsafeFrom(responses)
      thenRespond(iterator.next())
    }

    def thenRespondF(resp: => F[Response[_]]): Self = {
      val m: PartialFunction[GenericRequest[_, _], F[Response[_]]] = {
        case r if p(r) => resp
      }
      withMatchers(matchers.orElse(m))
    }
    def thenRespondF(resp: GenericRequest[_, _] => F[Response[_]]): Self = {
      val m: PartialFunction[GenericRequest[_, _], F[Response[_]]] = {
        case r if p(r) => resp(r)
      }
      withMatchers(matchers.orElse(m))
    }
  }
}

object AbstractBackendStub {

  private[client4] def tryAdjustResponseType[DesiredRType, RType, F[_]](
                                                                         ra: ResponseAsDelegate[DesiredRType, _],
                                                                         m: F[Response[RType]]
  )(implicit monad: MonadError[F]): F[Response[DesiredRType]] = {
    monad.flatMap[Response[RType], Response[DesiredRType]](m) { r =>
      tryAdjustResponseBody(ra.delegate, r.body, r).getOrElse(monad.unit(r.body)).map { nb =>
        r.copy(body = nb.asInstanceOf[DesiredRType])
      }
    }
  }

  private[client4] def tryAdjustResponseBody[F[_], T, U](
                                                          ra: GenericResponseAs[T, _],
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
          case ()              => Some(Array[Byte]().unit.asInstanceOf[F[T]])
          case _               => None
        }
      case ResponseAsStream(_, f) =>
        b match {
          case RawStream(s) => Some(monad.suspend(f.asInstanceOf[(Any, ResponseMetadata) => F[T]](s, meta)))
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
      case ResponseAsWebSocketStream(_, _) => None
      case MappedResponseAs(raw, g, _) =>
        tryAdjustResponseBody(raw, b, meta).map(_.flatMap(result => monad.eval(g(result, meta))))
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
}
