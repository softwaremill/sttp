package sttp.client.asynchttpclient

import java.nio.ByteBuffer

import _root_.zio._
import _root_.zio.stream._
import sttp.client._
import sttp.client.asynchttpclient.zio.ZioWebSocketHandler
import sttp.client.ws.{WebSocket, WebSocketResponse}

package object ziostreams {

  /**
    * Experimental! ZIO-environment service definition, which is an SttpBackend.
    */
  type SttpStreamsClient = Has[SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler]]

  /**
    * Experimental!
    */
  object SttpStreamsClient {

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
    def send[T](request: Request[T, Stream[Throwable, ByteBuffer]]): ZIO[SttpStreamsClient, Throwable, Response[T]] =
      ZIO.accessM(env => env.get[SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler]].send(request))

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
    ): ZIO[SttpStreamsClient, Throwable, WebSocketResponse[WebSocket[Task]]] =
      ZioWebSocketHandler().flatMap(handler =>
        ZIO.accessM(env => env.get[SttpBackend[Task, Nothing, WebSocketHandler]].openWebsocket(request, handler))
      )
  }

}
