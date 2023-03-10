package sttp.client4.httpclient

import _root_.zio._
import sttp.capabilities.Effect
import sttp.capabilities.zio.ZioStreams
import sttp.client4._

package object zio {

  /** Type alias to be used as the sttp ZIO service (mainly in ZIO environment). */
  type SttpClient = WebSocketStreamBackend[Task, ZioStreams]

  /** Sends the given request.
    *
    * The response body is deserialized as specified by this request (see [[Request.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are kept unchanged.
    *
    * @return
    *   An effect, containing a [[Response]], with the body handled as specified by this request (see
    *   [[Request.response]]). Or a failed effect, if an exception occurred when connecting to the target host, writing
    *   the request or reading the response.
    */
  def send[T](request: Request[T]): ZIO[SttpClient, Throwable, Response[T]] =
    ZIO.serviceWithZIO[SttpClient](request.send[Task])

  /** Sends the given request, where the request body or the response body is handled as a stream.
    *
    * The response body is deserialized as specified by this request (see [[Request.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are kept unchanged.
    *
    * @return
    *   An effect, containing a [[Response]], with the body handled as specified by this request (see
    *   [[Request.response]]). Or a failed effect, if an exception occurred when connecting to the target host, writing
    *   the request or reading the response.
    */
  def send[T, C >: ZioStreams with Effect[Task]](
      request: StreamRequest[T, C]
  ): ZIO[SttpClient, Throwable, Response[T]] =
    ZIO.serviceWithZIO[SttpClient](request.send[Task, ZioStreams])

  /** A variant of [[send]] which allows the effects that are part of the response handling specification (when using
    * resource-safe streaming) to use an `R` environment.
    */
  def sendR[T, C >: ZioStreams with Effect[RIO[R, *]], R](
      request: StreamRequest[T, C]
  ): ZIO[SttpClient with R, Throwable, Response[T]] = {
    import sttp.client4.impl.zio.StreamBackendExtendEnv
    ZIO.serviceWithZIO[SttpClient](b => request.send[RIO[R, *], ZioStreams](b.extendEnv[R]))
  }

  /** Sends the given WebSocket request.
    *
    * The response body is deserialized as specified by this request (see [[Request.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are kept unchanged.
    *
    * @return
    *   An effect, containing a [[Response]], with the body handled as specified by this request (see
    *   [[Request.response]]). Or a failed effect, if an exception occurred when connecting to the target host, writing
    *   the request or reading the response.
    */
  def send[T](request: WebSocketRequest[Task, T]): ZIO[SttpClient, Throwable, Response[T]] =
    ZIO.serviceWithZIO[SttpClient](request.send)

  /** A variant of [[send]] which allows the effects that are part of the response handling specification (when using
    * websockets or resource-safe streaming) to use an `R` environment.
    */
  def sendR[T, R](request: WebSocketRequest[RIO[R, *], T]): ZIO[SttpClient with R, Throwable, Response[T]] = {
    import sttp.client4.impl.zio.WebSocketBackendExtendEnv
    ZIO.serviceWithZIO[SttpClient](b => request.send(b.extendEnv[R]))
  }

  /** Sends the given WebSocket request, where the request body or the response WebSocket is handled as a stream.
    *
    * The response body is deserialized as specified by this request (see [[Request.response]]).
    *
    * Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are kept unchanged.
    *
    * @return
    *   An effect, containing a [[Response]], with the body handled as specified by this request (see
    *   [[Request.response]]). Or a failed effect, if an exception occurred when connecting to the target host, writing
    *   the request or reading the response.
    */
  def send[T](request: WebSocketStreamRequest[T, ZioStreams]): ZIO[SttpClient, Throwable, Response[T]] =
    ZIO.serviceWithZIO[SttpClient](request.send)
}
