package sttp.client.asynchttpclient

import _root_.zio._
import _root_.zio.stream._
import sttp.client._
import sttp.client.asynchttpclient.zio.SttpClientStubbing.StubbingWhenRequest
import sttp.client.impl.zio.SttpClientStubbingBase
import sttp.client.ws.{WebSocket, WebSocketResponse}

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

  object SttpClientStubbing extends SttpClientStubbingBase[Any, Stream[Throwable, Byte], WebSocketHandler] {
    override private[sttp] def serviceTag: Tag[SttpClientStubbing.Service] = implicitly
    override private[sttp] def sttpBackendTag: Tag[SttpClient.Service] = implicitly
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
