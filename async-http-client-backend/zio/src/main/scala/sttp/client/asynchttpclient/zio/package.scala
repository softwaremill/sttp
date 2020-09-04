package sttp.client.asynchttpclient

import _root_.zio._
import _root_.zio.stream._
import sttp.client._
import sttp.client.asynchttpclient.zio.SttpClientStubbing.StubbingWhenRequest
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.monad.MonadError
import sttp.client.testing.{SttpBackendStub, WebSocketStub}
import sttp.client.ws.{WebSocket, WebSocketResponse}
import sttp.model.{Headers, StatusCode}

package object zio {

  /**
    * ZIO-environment service definition, which is an SttpBackend.
    */
  type SttpClient = Has[SttpClient.Service]
  type SttpClientStubbing = Has[SttpClientStubbing.Service]

  object SttpClient {

    type Service = SttpBackend[Task, Stream[Throwable, Byte], WebSocketHandler]

    /**
      * Sends the request. Only requests for which the method & URI are specified can be sent.
      *
      * @return An effect resulting in a [[Response]], containing the body, deserialized as specified by the request
      *         (see [[RequestT.response]]), if the request was successful (1xx, 2xx, 3xx response codes), or if there
      *         was a protocol-level failure (4xx, 5xx response codes).
      *
      *         A failed effect, if an exception occurred when connecting to the target host, writing the request or
      *         reading the response.
      *
      *         Known exceptions are converted to one of [[SttpClientException]]. Other exceptions are kept unchanged.
      */
    def send[T](request: Request[T, Stream[Throwable, Byte]]): ZIO[SttpClient, Throwable, Response[T]] =
      ZIO.accessM(env => env.get[Service].send(request))

    /**
      * Opens a websocket. Only requests for which the method & URI are specified can be sent.
      *
      * @return An effect resulting in a [[WebSocketResponse]], containing a [[WebSocket]] instance allowing sending
      *         and receiving messages, if the request was successful and the connection was successfully upgraded to a
      *         websocket.
      *
      *         A failed effect, if an exception occurred when connecting to the target host, writing the request,
      *         reading the response or upgrading to a websocket.
      *
      *         Known exceptions are converted to one of [[SttpClientException]]. Other exceptions are kept unchanged.
      */
    def openWebsocket[T, WS_RESULT](
        request: Request[T, Nothing]
    ): ZIO[SttpClient, Throwable, WebSocketResponse[WebSocket[Task]]] =
      ZioWebSocketHandler().flatMap(handler => ZIO.accessM(env => env.get[Service].openWebsocket(request, handler)))
  }

  object SttpClientStubbing {

    trait Service {
      def whenRequestMatchesPartial(
          partial: PartialFunction[Request[_, _], Response[_]]
      ): URIO[SttpClientStubbing, Unit]

      private[zio] def update(
          stub: SttpBackendStub[Task, Stream[Throwable, Byte], WebSocketHandler] => SttpBackendStub[
            Task,
            Stream[Throwable, Byte],
            WebSocketHandler
          ]
      ): UIO[Unit]
    }

    private class StubWrapper(stub: Ref[SttpBackendStub[Task, Stream[Throwable, Byte], WebSocketHandler]])
        extends Service {
      override def whenRequestMatchesPartial(
          partial: PartialFunction[Request[_, _], Response[_]]
      ): URIO[SttpClientStubbing, Unit] =
        update(_.whenRequestMatchesPartial(partial))

      override private[zio] def update(
          f: SttpBackendStub[Task, Stream[Throwable, Byte], WebSocketHandler] => SttpBackendStub[
            Task,
            Stream[Throwable, Byte],
            WebSocketHandler
          ]
      ) = stub.update(f)
    }

    final case class StubbingWhenRequest private[zio] (p: Request[_, _] => Boolean) {
      val thenRespondOk: URIO[SttpClientStubbing, Unit] =
        thenRespondWithCode(StatusCode.Ok)

      def thenRespondNotFound(): URIO[SttpClientStubbing, Unit] =
        thenRespondWithCode(StatusCode.NotFound, "Not found")

      def thenRespondServerError(): URIO[SttpClientStubbing, Unit] =
        thenRespondWithCode(StatusCode.InternalServerError, "Internal server error")

      def thenRespondWithCode(status: StatusCode, msg: String = ""): URIO[SttpClientStubbing, Unit] = {
        thenRespond(Response(msg, status, msg))
      }

      def thenRespond[T](body: T): URIO[SttpClientStubbing, Unit] =
        thenRespond(Response[T](body, StatusCode.Ok, "OK"))

      def thenRespond[T](resp: => Response[T]): URIO[SttpClientStubbing, Unit] =
        URIO.accessM(_.get.update(_.whenRequestMatches(p).thenRespond(resp)))

      def thenRespondCyclic[T](bodies: T*): URIO[SttpClientStubbing, Unit] = {
        thenRespondCyclicResponses(bodies.map(body => Response[T](body, StatusCode.Ok, "OK")): _*)
      }

      def thenRespondCyclicResponses[T](responses: Response[T]*): URIO[SttpClientStubbing, Unit] = {
        for {
          q <- Queue.bounded[Response[T]](responses.length)
          _ <- q.offerAll(responses)
          _ <- thenRespondWrapped(q.take.flatMap(t => q.offer(t) as t))
        } yield ()
      }

      def thenRespondWrapped(resp: => Task[Response[_]]): URIO[SttpClientStubbing, Unit] = {
        val m: PartialFunction[Request[_, _], Task[Response[_]]] = {
          case r if p(r) => resp
        }
        URIO.accessM(_.get.update(_.whenRequestMatches(p).thenRespondWrapped(m)))
      }

      def thenRespondWrapped(resp: Request[_, _] => Task[Response[_]]): URIO[SttpClientStubbing, Unit] = {
        val m: PartialFunction[Request[_, _], Task[Response[_]]] = {
          case r if p(r) => resp(r)
        }
        URIO.accessM(_.get.update(_.whenRequestMatches(p).thenRespondWrapped(m)))
      }

      def thenRespondWebSocket[WS_RESULT](result: WS_RESULT): URIO[SttpClientStubbing, Unit] =
        thenRespondWebSocket(Headers(List.empty), result)

      def thenRespondWebSocket[WS_RESULT](headers: Headers, result: WS_RESULT): URIO[SttpClientStubbing, Unit] =
        URIO.accessM(_.get.update(_.whenRequestMatches(p).thenRespondWebSocket(headers, result)))

      def thenRespondWebSocket(wsStub: WebSocketStub[_]): URIO[SttpClientStubbing, Unit] =
        thenRespondWebSocket(Headers(List.empty), wsStub)

      def thenRespondWebSocket(headers: Headers, wsStub: WebSocketStub[_]): URIO[SttpClientStubbing, Unit] =
        URIO.accessM(_.get.update(_.whenRequestMatches(p).thenRespondWebSocket(headers, wsStub)))

      def thenHandleOpenWebSocket[WS_RESULT](
          useHandler: WebSocketHandler[WS_RESULT] => WS_RESULT
      ): URIO[SttpClientStubbing, Unit] =
        thenHandleOpenWebSocket(Headers(List.empty), useHandler)

      def thenHandleOpenWebSocket[WS_RESULT](
          headers: Headers,
          useHandler: WebSocketHandler[WS_RESULT] => WS_RESULT
      ): URIO[SttpClientStubbing, Unit] = {
        URIO.accessM(_.get.update(_.whenRequestMatches(p).thenHandleOpenWebSocket(headers, useHandler)))
      }

    }

    val layer: ZLayer[Any, Nothing, SttpClientStubbing with SttpClient] =
      ZLayer.fromEffectMany(for {
        stub <- Ref.make(AsyncHttpClientZioBackend.stub)
        stubber = new StubWrapper(stub)
        proxy = new SttpClient.Service {
          override def send[T](request: Request[T, Stream[Throwable, Byte]]): Task[Response[T]] =
            stub.get >>= (_.send(request))

          override def openWebsocket[T, WS_RESULT](
              request: Request[T, Stream[Throwable, Byte]],
              handler: WebSocketHandler[WS_RESULT]
          ): Task[WebSocketResponse[WS_RESULT]] =
            stub.get >>= (_.openWebsocket(request, handler))

          override def close(): Task[Unit] =
            stub.get >>= (_.close())

          override def responseMonad: MonadError[Task] = new RIOMonadAsyncError[Any]()
        }
      } yield Has.allOf[SttpClientStubbing.Service, SttpClient.Service](stubber, proxy))
  }

  object stubbing {
    def whenRequestMatches(p: Request[_, _] => Boolean): StubbingWhenRequest =
      StubbingWhenRequest(p)

    val whenAnyRequest: StubbingWhenRequest =
      StubbingWhenRequest(_ => true)

    def whenRequestMatchesPartial(
        partial: PartialFunction[Request[_, _], Response[_]]
    ): URIO[SttpClientStubbing, Unit] =
      ZIO.accessM(_.get.whenRequestMatchesPartial(partial))
  }

}
