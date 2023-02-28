package sttp.client3.armeria

import _root_.zio._
import sttp.capabilities.Effect
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.impl.zio.ExtendEnv

package object zio {

  /** Type alias to be used as the sttp ZIO service (mainly in ZIO environment). */
  type SttpClient = SttpBackend[Task, ZioStreams]

  /** Sends the request. Only requests for which the method & URI are specified can be sent.
    *
    * @return
    *   An effect resulting in a`Response`, containing the body, deserialized as specified by the request (see
    *   `RequestT.response`), if the request was successful (1xx, 2xx, 3xx response codes), or if there was a
    *   protocol-level failure (4xx, 5xx response codes).
    *
    * A failed effect, if an exception occurred when connecting to the target host, writing the request or reading the
    * response.
    *
    * Known exceptions are converted to one of `SttpClientException`. Other exceptions are kept unchanged.
    */
  def send[T](request: Request[T, Effect[Task] with ZioStreams]): ZIO[SttpClient, Throwable, Response[T]] =
    ZIO.serviceWithZIO[SttpClient](_.send(request))

  /** A variant of `send` which allows the effects that are part of the response handling specification (when using
    * websockets or resource-safe streaming) to use an `R` environment.
    */
  def sendR[T, R](
      request: Request[T, Effect[RIO[R, *]] with ZioStreams]
  ): ZIO[SttpClient with R, Throwable, Response[T]] =
    ZIO.serviceWithZIO[SttpClient](_.extendEnv[R].send(request))
}
