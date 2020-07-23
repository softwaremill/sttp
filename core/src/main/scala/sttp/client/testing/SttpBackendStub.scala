package sttp.client.testing

import java.io.InputStream

import sttp.client._
import sttp.client.internal._

import sttp.client.monad.{FutureMonad, IdMonad, MonadError}
import sttp.client.testing.SttpBackendStub._
import sttp.client.internal.SttpFile
import sttp.client.ws.{WebSocket, WebSocketEvent, WebSocketResponse}
import sttp.client.{IgnoreResponse, ResponseAs, ResponseAsByteArray, SttpBackend}
import sttp.client.SttpClientException.ReadException
import sttp.model.{Headers, StatusCode}
import sttp.model.ws.WebSocketFrame

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
class SttpBackendStub[F[_], P, WS_HANDLER[_]](
    monad: MonadError[F],
    matchers: PartialFunction[Request[_, _], F[Response[_]]],
    wsMatchers: PartialFunction[Request[_, _], WhenOpenWebsocket[F, WS_HANDLER]],
    fallback: Option[SttpBackend[F, P, WS_HANDLER]]
) extends SttpBackend[F, P, WS_HANDLER] {

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
  def whenRequestMatchesPartial(
      partial: PartialFunction[Request[_, _], Response[_]]
  ): SttpBackendStub[F, P, WS_HANDLER] = {
    val wrappedPartial: PartialFunction[Request[_, _], F[Response[_]]] =
      partial.andThen((r: Response[_]) => monad.unit(r))
    new SttpBackendStub[F, P, WS_HANDLER](monad, matchers.orElse(wrappedPartial), wsMatchers, fallback)
  }

  /**
    * Specify how the stub backend should respond to open websocket requests
    * using the given partial function.
    */
  def whenRequestMatchesPartialReturnWebSocketResponse[WS_RESULT](
      partial: PartialFunction[Request[_, _], (Headers, WS_RESULT)]
  ): SttpBackendStub[F, P, WS_HANDLER] = {
    val wrappedPartial: PartialFunction[Request[_, _], WhenOpenWebsocket[F, WS_HANDLER]] =
      partial.andThen((r) => ReturnWebsocketResponse(r._1, () => monad.unit(r._2)))
    new SttpBackendStub[F, P, WS_HANDLER](monad, matchers, wsMatchers.orElse(wrappedPartial), fallback)
  }

  /**
    * Specify how the stub backend should use web socket handler using the given partial function.
    * Meant mainly for akka backend or cases when implementing a custom WS_HANDLER.
    */
  def whenRequestMatchesPartialHandleOpenWebsocket[WS_RESULT](
      partial: PartialFunction[Request[_, _], (Headers, WS_HANDLER[WS_RESULT] => WS_RESULT)]
  ): SttpBackendStub[F, P, WS_HANDLER] = {
    val wrappedPartial: PartialFunction[Request[_, _], WhenOpenWebsocket[F, WS_HANDLER]] =
      partial.andThen((r) => UseHandler(r._1, r._2.asInstanceOf[Any => WS_RESULT]))
    new SttpBackendStub[F, P, WS_HANDLER](monad, matchers, wsMatchers.orElse(wrappedPartial), fallback)
  }

  override def send[T, R >: P](request: Request[T, R]): F[Response[T]] = {
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

  override def openWebsocket[T, WR, R >: P](
      request: Request[T, R],
      handler: WS_HANDLER[WR]
  ): F[WebSocketResponse[WR]] = {
    Try(wsMatchers.lift(request)) match {
      case Success(Some(UseHandler(headers, useHandler))) =>
        val use = useHandler.asInstanceOf[WS_HANDLER[WR] => WR]
        monad.unit(WebSocketResponse(headers, use(handler)))
      case Success(Some(ReturnWebsocketResponse(headers, response))) =>
        monad.map(response())(r => WebSocketResponse(headers, r.asInstanceOf[WR]))
      case Success(None) =>
        fallback match {
          case None     => monad.error(new ReadException(new Exception("request didn't match any stub path")))
          case Some(fb) => fb.openWebsocket(request, handler)
        }
      case Failure(e) => monad.error(e)
    }
  }

  private def wrapResponse[T](r: Response[_]): F[Response[T]] =
    monad.unit(r.asInstanceOf[Response[T]])

  override def close(): F[Unit] = monad.unit(())

  override def responseMonad: MonadError[F] = monad

  class WhenRequest(p: Request[_, _] => Boolean) {
    def thenRespondOk(): SttpBackendStub[F, P, WS_HANDLER] =
      thenRespondWithCode(StatusCode.Ok)
    def thenRespondNotFound(): SttpBackendStub[F, P, WS_HANDLER] =
      thenRespondWithCode(StatusCode.NotFound, "Not found")
    def thenRespondServerError(): SttpBackendStub[F, P, WS_HANDLER] =
      thenRespondWithCode(StatusCode.InternalServerError, "Internal server error")
    def thenRespondWithCode(status: StatusCode, msg: String = ""): SttpBackendStub[F, P, WS_HANDLER] = {
      thenRespond(Response(msg, status, msg))
    }
    def thenRespond[T](body: T): SttpBackendStub[F, P, WS_HANDLER] =
      thenRespond(Response[T](body, StatusCode.Ok, "OK"))
    def thenRespond[T](resp: => Response[T]): SttpBackendStub[F, P, WS_HANDLER] = {
      val m: PartialFunction[Request[_, _], F[Response[_]]] = {
        case r if p(r) => monad.eval(resp)
      }
      new SttpBackendStub[F, P, WS_HANDLER](monad, matchers.orElse(m), wsMatchers, fallback)
    }

    /**
      * When [[openWebsocket()]] is called, the passed handler will be ignored, and the given result will be returned.
      * This method of stubbing is best suited when using the "high-level" websockets, that is when `WS_RESULT` is
      * [[WebSocket]].
      */
    def thenRespondWebSocket[WS_RESULT](result: WS_RESULT): SttpBackendStub[F, P, WS_HANDLER] =
      thenRespondWebSocket(Headers(List.empty), result)

    /**
      * When [[openWebsocket()]] is called, the passed handler will be ignored, and the given result will be returned.
      * This method of stubbing is best suited when using the "high-level" websockets, that is when `WS_RESULT` is
      * [[WebSocket]].
      */
    def thenRespondWebSocket[WS_RESULT](headers: Headers, result: WS_RESULT): SttpBackendStub[F, P, WS_HANDLER] = {
      val m: PartialFunction[Request[_, _], WhenOpenWebsocket[F, WS_HANDLER]] = {
        case r if p(r) =>
          ReturnWebsocketResponse(headers, () => monad.unit(result))
      }
      new SttpBackendStub[F, P, WS_HANDLER](monad, matchers, wsMatchers.orElse(m), fallback)
    }

    /**
      * When [[openWebsocket()]] is called, the passed handler will be ignored, and the given result will be returned.
      * This method of stubbing should be used when using the "high-level" websockets, that is when `WS_RESULT` is
      * [[WebSocket]].
      *
      * The websocket instance will be built using the given [[WebSocketStub]].
      */
    def thenRespondWebSocket(wsStub: WebSocketStub[_]): SttpBackendStub[F, P, WS_HANDLER] =
      thenRespondWebSocket(Headers(List.empty), wsStub)

    /**
      * When [[openWebsocket()]] is called, the passed handler will be ignored, and the given result will be returned.
      * This method of stubbing should be used when using the "high-level" websockets, that is when `WS_RESULT` is
      * [[WebSocket]].
      *
      * The websocket instance will be built using the given [[WebSocketStub]].
      */
    def thenRespondWebSocket(headers: Headers, wsStub: WebSocketStub[_]): SttpBackendStub[F, P, WS_HANDLER] = {
      val m: PartialFunction[Request[_, _], WhenOpenWebsocket[F, WS_HANDLER]] = {
        case r if p(r) =>
          ReturnWebsocketResponse(headers, () => monad.unit(wsStub.build(monad)))
      }
      new SttpBackendStub[F, P, WS_HANDLER](monad, matchers, wsMatchers.orElse(m), fallback)
    }

    /**
      * When [[openWebsocket()]] is called, the given headers and handler are used to create the result.
      * This method of stubbing is best suited when [[openWebsocket()]] is called with a handler that
      * doesn't return a "high-level" [[WebSocket]], but instead e.g. a backend-specific stream.
      */
    def thenHandleOpenWebSocket[WS_RESULT](
        useHandler: WS_HANDLER[WS_RESULT] => WS_RESULT
    ): SttpBackendStub[F, P, WS_HANDLER] = thenHandleOpenWebSocket(Headers(List.empty), useHandler)

    /**
      * When [[openWebsocket()]] is called, the given headers and handler are used to create the result.
      * This method of stubbing is best suited when [[openWebsocket()]] is called with a handler that
      * doesn't return a "high-level" [[WebSocket]], but instead e.g. a backend-specific stream.
      */
    def thenHandleOpenWebSocket[WS_RESULT](
        headers: Headers,
        useHandler: WS_HANDLER[WS_RESULT] => WS_RESULT
    ): SttpBackendStub[F, P, WS_HANDLER] = {
      val m: PartialFunction[Request[_, _], WhenOpenWebsocket[F, WS_HANDLER]] = {
        case r if p(r) =>
          UseHandler(headers, useHandler.asInstanceOf[Any => WS_RESULT])
      }
      new SttpBackendStub[F, P, WS_HANDLER](monad, matchers, wsMatchers.orElse(m), fallback)
    }

    /**
      * Not thread-safe!
      */
    def thenRespondCyclic[T](bodies: T*): SttpBackendStub[F, P, WS_HANDLER] = {
      thenRespondCyclicResponses(bodies.map(body => Response[T](body, StatusCode.Ok, "OK")): _*)
    }

    /**
      * Not thread-safe!
      */
    def thenRespondCyclicResponses[T](responses: Response[T]*): SttpBackendStub[F, P, WS_HANDLER] = {
      val iterator = Iterator.continually(responses).flatten
      thenRespond(iterator.next)
    }
    def thenRespondWrapped(resp: => F[Response[_]]): SttpBackendStub[F, P, WS_HANDLER] = {
      val m: PartialFunction[Request[_, _], F[Response[_]]] = {
        case r if p(r) => resp
      }
      new SttpBackendStub[F, P, WS_HANDLER](monad, matchers.orElse(m), wsMatchers, fallback)
    }
    def thenRespondWrapped(resp: Request[_, _] => F[Response[_]]): SttpBackendStub[F, P, WS_HANDLER] = {
      val m: PartialFunction[Request[_, _], F[Response[_]]] = {
        case r if p(r) => resp(r)
      }
      new SttpBackendStub[F, P, WS_HANDLER](monad, matchers.orElse(m), wsMatchers, fallback)
    }
  }
}

object SttpBackendStub {

  /**
    * Create a stub of a synchronous backend (which doesn't wrap results in any
    * container), without streaming or websocket support.
    */
  def synchronous[WS_HANDLER[_]]: SttpBackendStub[Identity, Any, WS_HANDLER] =
    new SttpBackendStub(
      IdMonad,
      PartialFunction.empty,
      PartialFunction.empty,
      None
    )

  /**
    * Create a stub of an asynchronous backend (which wraps results in Scala's
    * built-in [[Future]]), without streaming or websocket support.
    */
  def asynchronousFuture[WS_HANDLER[_]]: SttpBackendStub[Future, Any, WS_HANDLER] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    new SttpBackendStub(
      new FutureMonad(),
      PartialFunction.empty,
      PartialFunction.empty,
      None
    )
  }

  /**
    * Create a stub backend using the given response monad (which determines
    * how requests are wrapped), any stream type and any websocket handler.
    */
  def apply[F[_], P, WS_HANDLER[_]](responseMonad: MonadError[F]): SttpBackendStub[F, P, WS_HANDLER] =
    new SttpBackendStub[F, P, WS_HANDLER](
      responseMonad,
      PartialFunction.empty,
      PartialFunction.empty,
      None
    )

  /**
    * Create a stub backend which delegates send requests to the given fallback
    * backend, if the request doesn't match any of the specified predicates.
    */
  def withFallback[F[_], P, R >: P, WS_HANDLER[_]](
      fallback: SttpBackend[F, P, WS_HANDLER]
  ): SttpBackendStub[F, R, WS_HANDLER] =
    new SttpBackendStub[F, R, WS_HANDLER](
      fallback.responseMonad,
      PartialFunction.empty,
      PartialFunction.empty,
      Some(fallback)
    )

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
      case ResponseAsStream(_) =>
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

/**
  * Websockets use can differ, for akka backend the crucial part is the handler.
  * For other backends sttp provides handlers that return [[WebSocket]]
  * instance to send and receive messages.
  */
private[testing] sealed trait WhenOpenWebsocket[F[_], +WS_HANDLER[_]]

private[testing] case class UseHandler[F[_], WS_RESULT, WS_HANDLER[_]](
    headers: Headers,
    useHandler: Any => WS_RESULT
) extends WhenOpenWebsocket[F, WS_HANDLER]

private[testing] case class ReturnWebsocketResponse[F[_], WS_RESULT](headers: Headers, response: () => F[WS_RESULT])
    extends WhenOpenWebsocket[F, NothingT]

/**
  * A simple stub for websockets that uses a queue of responses which are returned when the client calls
  * [[WebSocket.receive]].
  *
  * New messages can be added to queue in reaction to [[WebSocket.send]] being invoked, by specifying the
  * behavior using one of the [[thenRespond]] variatns.
  *
  * For more complex cases, please provide your own implementation of [[WebSocket]].
  */
class WebSocketStub[S](
    initialResponses: List[Try[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]]],
    initialState: S,
    makeNewResponses: (S, WebSocketFrame) => (S, List[Try[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]]])
) {

  /**
    * Creates a stub that has the same initial responses, but replaces the function that adds messages to be
    * received using [[WebSocket.receive]], in reaction to [[WebSocket.send]] being invoked.
    */
  def thenRespond(addReceived: WebSocketFrame => List[WebSocketFrame.Incoming]): WebSocketStub[Unit] =
    thenRespondWith(
      addReceived.andThen(_.map(m => Success(Right(m): Either[WebSocketEvent.Close, WebSocketFrame.Incoming])))
    )

  /**
    * Creates a stub that has the same initial responses, but replaces the function that adds responses to be
    * received using [[WebSocket.receive]], in reaction to [[WebSocket.send]] being invoked.
    *
    * More powerful version of [[thenRespond]], as can result in the websocket to become closed.
    */
  def thenRespondWith(
      addReceived: WebSocketFrame => List[Try[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]]]
  ): WebSocketStub[Unit] =
    new WebSocketStub(
      initialResponses,
      (),
      (_, frame) => ((), addReceived(frame))
    )

  /**
    * Creates a stub that has the same initial responses, but replaces the function that adds responses to be
    * received using [[WebSocket.receive]], in reaction to [[WebSocket.send]] being invoked.
    *
    * More powerful version of [[thenRespond]], as the given function can additionally use state and implement stateful
    * logic for computing response messages.
    */
  def thenRespondS[S2](initial: S2)(
      onSend: (S2, WebSocketFrame) => (S2, List[WebSocketFrame.Incoming])
  ): WebSocketStub[S2] =
    thenRespondWithS(initial)((state, frame) => {
      val (newState, messages) = onSend(state, frame)
      (newState, messages.map(m => Success(Right(m): Either[WebSocketEvent.Close, WebSocketFrame.Incoming])))
    })

  /**
    * Creates a stub that has the same initial responses, but replaces the function that adds responses to be
    * received using [[WebSocket.receive]], in reaction to [[WebSocket.send]] being invoked.
    *
    * More powerful version of [[thenRespond]], as the given function can additionally use state and implement stateful
    * logic for computing response messages, as well as result in the websocket to become closed.
    */
  def thenRespondWithS[S2](initial: S2)(
      onSend: (S2, WebSocketFrame) => (S2, List[Try[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]]])
  ): WebSocketStub[S2] = new WebSocketStub(initialResponses, initial, onSend)

  private[testing] def build[F[_]](implicit m: MonadError[F]): WebSocket[F] =
    new WebSocket[F] {

      private var state: S = initialState
      private var _isOpen: Boolean = true
      private var responses = initialResponses.toList

      override def monad: MonadError[F] = m
      override def isOpen: F[Boolean] = monad.unit(_isOpen)

      override def receive: F[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]] =
        synchronized {
          if (_isOpen) {
            responses.headOption match {
              case Some(Success(Right(response))) =>
                responses = responses.tail
                monad.unit(Right(response))
              case Some(Success(Left(close))) =>
                _isOpen = false
                monad.unit(Left(close))
              case Some(Failure(e)) =>
                _isOpen = false
                monad.error(e)
              case None =>
                monad.error(new Exception("Unexpected 'receive', no more prepared responses."))
            }
          } else {
            monad.error(new Exception("WebSocket is closed."))
          }
        }

      override def send(frame: WebSocketFrame, isContinuation: Boolean): F[Unit] =
        monad.flatten(monad.eval {
          synchronized {
            if (_isOpen) {
              val (newState, newResponses) = makeNewResponses(state, frame)
              responses = responses ++ newResponses
              state = newState
              monad.unit(())
            } else {
              monad.error(new Exception("WebSocket is closed."))
            }
          }
        })
    }
}

object WebSocketStub {

  /**
    * Creates a stub which will return the given responses when [[WebSocket.receive]] is called by the client.
    * More messages can be enqueued to be returned by the stub by subsequently calling one of the
    * [[WebSocketStub.thenRespond]] methods.
    */
  def withInitialResponses(
      events: List[Try[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]]]
  ): WebSocketStub[Unit] = {
    new WebSocketStub(events, (), (_, _) => ((), List.empty))
  }

  /**
    * Creates a stub which will return the given messages when [[WebSocket.receive]] is called by the client.
    * More messages can be enqueued to be returned by the stub by subsequently calling one of the
    * [[WebSocketStub.thenRespond]] methods.
    */
  def withInitialIncoming(
      messages: List[WebSocketFrame.Incoming]
  ): WebSocketStub[Unit] = {
    withInitialResponses(messages.map(m => Success(Right(m): Either[WebSocketEvent.Close, WebSocketFrame.Incoming])))
  }

  /**
    * Creates a stub which won't return any initial responses when [[WebSocket.receive]] is called by the client.
    * Messages can be enqueued to be returned by the stub by subsequently calling one of the
    * [[WebSocketStub.thenRespond]] methods.
    */
  def withNoInitialResponses: WebSocketStub[Unit] = withInitialResponses(List.empty)

}
