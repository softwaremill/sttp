package sttp.client4.testing

import java.io.{ByteArrayInputStream, InputStream}
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
import sttp.model.StatusText

abstract class AbstractBackendStub[F[_], P](
    _monad: MonadError[F],
    matchers: PartialFunction[GenericRequest[_, _], F[Response[StubBody]]],
    fallback: Option[GenericBackend[F, P]]
) extends GenericBackend[F, P] {
  type Self
  protected def withMatchers(matchers: PartialFunction[GenericRequest[_, _], F[Response[StubBody]]]): Self
  override def monad: MonadError[F] = _monad

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
  def whenRequestMatchesPartial(partial: PartialFunction[GenericRequest[_, _], Response[StubBody]]): Self = {
    val wrappedPartial: PartialFunction[GenericRequest[_, _], F[Response[StubBody]]] =
      partial.andThen((r: Response[StubBody]) => monad.unit(r))
    withMatchers(matchers.orElse(wrappedPartial))
  }

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] = monad.suspend {
    Try(matchers.lift(request)) match {
      case Success(Some(response)) =>
        adjustExceptions(request) {
          monad.flatMap(response) { r =>
            request.options.onBodyReceived(r)

            r.body match {
              case StubBody.Exact(v) => monad.unit(r.copy(body = v.asInstanceOf[T]))
              case StubBody.Adjust(v) =>
                monad.map(adjustResponseBody(request.response.delegate, v, r.asInstanceOf[Response[T]])(monad))(b =>
                  r.copy(body = b)
                )
            }
          }
        }
      case Success(None) =>
        fallback match {
          case None     => monad.error(new IllegalArgumentException(s"No behavior stubbed for request: $request"))
          case Some(fb) => fb.send(request)
        }
      case Failure(e) =>
        monad.flatMap {
          ResponseException.find(e) match {
            case Some(re) => monad.eval(request.options.onBodyReceived(re.response))
            case None     => monad.unit(())
          }
        } { _ => adjustExceptions(request)(monad.error(e)) }
    }
  }

  private def adjustExceptions[T](request: GenericRequest[_, _])(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(monad)(t)(
      SttpClientException.defaultExceptionToSttpClientException(request, _)
    )

  override def close(): F[Unit] = monad.unit(())

  class WhenRequest(p: GenericRequest[_, _] => Boolean) {

    /** Respond with an empty body and the 200 status code */
    def thenRespondOk(): Self = thenRespondWithCode(StatusCode.Ok)
    def thenRespondBadRequest(): Self = thenRespondWithCode(StatusCode.BadRequest)
    def thenRespondNotFound(): Self = thenRespondWithCode(StatusCode.NotFound)
    def thenRespondServerError(): Self = thenRespondWithCode(StatusCode.InternalServerError)
    def thenRespondUnauthorized(): Self = thenRespondWithCode(StatusCode.Unauthorized)
    def thenRespondForbidden(): Self = thenRespondWithCode(StatusCode.Forbidden)

    /** Respond with an empty body (for 1xx/2xx responses), or a body with an error message (for 4xx/5xx responses) and
      * the given status code.
      */
    def thenRespondWithCode(code: StatusCode): Self =
      thenRespondAdjust(
        if (code.isClientError || code.isServerError) StatusText.default(code).getOrElse("") else "",
        code
      )

    /** Adjust the given body, as specified in the request's response handling description. */
    def thenRespondAdjust(body: Any): Self = thenRespond(ResponseStub.adjust(body))

    /** Adjust the given body, as specified in the request's response handling description. */
    def thenRespondAdjust(body: Any, code: StatusCode): Self = thenRespond(ResponseStub.adjust(body, code))

    /** Respond with the given body, regardless of what's specified in the request's response handling description. */
    def thenRespondExact(body: Any): Self = thenRespond(ResponseStub.exact(body))

    /** Respond with the given body, regardless of what's specified in the request's response handling description. */
    def thenRespondExact(body: Any, code: StatusCode): Self = thenRespond(ResponseStub.exact(body, code))

    def thenThrow(e: Throwable): Self = {
      val m: PartialFunction[GenericRequest[_, _], F[Response[StubBody]]] = {
        case r if p(r) => monad.error(e)
      }
      withMatchers(matchers.orElse(m))
    }

    /** Response with the given response (lazily evaluated). To create responses, use [[ResponseStub]]. */
    def thenRespond[T](resp: => Response[StubBody]): Self = {
      val m: PartialFunction[GenericRequest[_, _], F[Response[StubBody]]] = {
        case r if p(r) => monad.eval(resp.copy(request = r.onlyMetadata))
      }
      withMatchers(matchers.orElse(m))
    }

    /** Response with the given responses, in a loop. To create responses, use [[ResponseStub]]. */
    def thenRespondCyclic(responses: Response[StubBody]*): Self = {
      val iterator = AtomicCyclicIterator.unsafeFrom(responses)
      thenRespond(iterator.next())
    }

    /** Response with the given response, given as an F-effect. To create responses, use [[ResponseStub]]. */
    def thenRespondF(resp: => F[Response[StubBody]]): Self = {
      val m: PartialFunction[GenericRequest[_, _], F[Response[StubBody]]] = {
        case r if p(r) => resp
      }
      withMatchers(matchers.orElse(m))
    }

    /** Response with the given response, given as an F-effect, created basing on the received request. To create
      * responses, use [[ResponseStub]].
      */
    def thenRespondF(resp: GenericRequest[_, _] => F[Response[StubBody]]): Self = {
      val m: PartialFunction[GenericRequest[_, _], F[Response[StubBody]]] = {
        case r if p(r) => resp(r)
      }
      withMatchers(matchers.orElse(m))
    }
  }
}

object AbstractBackendStub {
  private def adjustResponseBody[F[_], T, U](
      ra: GenericResponseAs[T, _],
      b: U,
      meta: ResponseMetadata
  )(implicit monad: MonadError[F]): F[T] = {
    def bAsInputStream = b match {
      case s: String       => (new ByteArrayInputStream(s.getBytes(Utf8))).unit
      case a: Array[Byte]  => (new ByteArrayInputStream(a)).unit
      case is: InputStream => is.unit
      case ()              => (new ByteArrayInputStream(new Array[Byte](0))).unit
      case _ =>
        monad.error(throw new IllegalArgumentException(s"Provided body: $b, cannot be adjusted to an input stream"))
    }

    ra match {
      case IgnoreResponse => ().unit.asInstanceOf[F[T]]
      case ResponseAsByteArray =>
        b match {
          case s: String       => s.getBytes(Utf8).unit.asInstanceOf[F[T]]
          case a: Array[Byte]  => a.unit.asInstanceOf[F[T]]
          case is: InputStream => toByteArray(is).unit.asInstanceOf[F[T]]
          case ()              => Array[Byte]().unit.asInstanceOf[F[T]]
          case _ => monad.error(new IllegalArgumentException(s"Provided body: $b, cannot be adjusted to a byte array"))
        }
      case ResponseAsStream(_, f)      => monad.suspend(f.asInstanceOf[(Any, ResponseMetadata) => F[T]](b, meta))
      case ResponseAsStreamUnsafe(_)   => b.unit.asInstanceOf[F[T]]
      case ResponseAsInputStream(f)    => bAsInputStream.map(f).asInstanceOf[F[T]]
      case ResponseAsInputStreamUnsafe => bAsInputStream.asInstanceOf[F[T]]
      case ResponseAsFile(_) =>
        b match {
          case f: SttpFile => f.unit.asInstanceOf[F[T]]
          case _ => monad.error(new IllegalArgumentException(s"Provided body: $b, cannot be adjusted to a file"))
        }
      case ResponseAsWebSocket(f) =>
        b match {
          case wss: WebSocketStub[_] =>
            f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[T]](wss.build[F](monad), meta)
          case ws: WebSocket[_] =>
            f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[T]](ws.asInstanceOf[WebSocket[F]], meta)
          case _ =>
            monad.error(
              new IllegalArgumentException(
                s"Provided body: $b is neither a WebSocket, nor a WebSocketStub instance"
              )
            )
        }
      case ResponseAsWebSocketUnsafe() =>
        b match {
          case wss: WebSocketStub[_] => wss.build[F](monad).unit.asInstanceOf[F[T]]
          case ws: WebSocket[_]      => ws.asInstanceOf[WebSocket[F]].unit.asInstanceOf[F[T]]
          case _ =>
            monad.error(
              new IllegalArgumentException(
                s"Provided body: $b is neither a WebSocket, nor a WebSocketStub instance"
              )
            )
        }
      case ResponseAsWebSocketStream(_, _) =>
        monad.error(
          new IllegalArgumentException(
            "Stubbing responses for asWebSocketStream response descriptions is not supported"
          )
        )
      case MappedResponseAs(raw, g, _) =>
        adjustResponseBody(raw, b, meta).flatMap(result => monad.eval(g(result, meta)))
      case rfm: ResponseAsFromMetadata[_, _] => adjustResponseBody(rfm(meta), b, meta)
      case ResponseAsBoth(l, r) =>
        adjustResponseBody(l, b, meta)
          .flatMap { lAdjusted =>
            adjustResponseBody(r, b, meta)
              .map(rAdjusted => (lAdjusted, Option(rAdjusted)))
              .handleError { case _: IllegalArgumentException =>
                monad.unit((lAdjusted, Option.empty))
              }
          }
          .asInstanceOf[F[T]] // needed by Scala2
    }
  }
}
