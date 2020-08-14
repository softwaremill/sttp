package sttp.client.httpclient

import sttp.client._
import _root_.zio._
import _root_.zio.blocking.Blocking
import sttp.capabilities.zio.BlockingZioStreams
import sttp.capabilities.{Effect, WebSockets}
import sttp.client.impl.zio.ExtendEnv

package object zio {

  type BlockingTask[A] = RIO[Blocking, A]

  /**
    * ZIO-environment service definition, which is an SttpBackend.
    */
  type SttpClient = Has[SttpBackend[BlockingTask, BlockingZioStreams with WebSockets]]

  object SttpClient {

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
    def send[T](
        request: Request[T, BlockingZioStreams with Effect[BlockingTask] with WebSockets]
    ): RIO[SttpClient with Blocking, Response[T]] =
      ZIO.accessM(env => env.get[SttpBackend[BlockingTask, BlockingZioStreams with WebSockets]].send(request))

    /**
      * A variant of [[send]] which allows the effects that are part of the response handling specification to use
      * the `Blocking with R` environment.
      */
    def sendR[T, R](
        request: Request[T, Effect[RIO[Blocking with R, *]] with BlockingZioStreams with WebSockets]
    ): RIO[SttpClient with Blocking with R, Response[T]] =
      ZIO.accessM(env =>
        env.get[SttpBackend[BlockingTask, BlockingZioStreams with WebSockets]].extendEnv[R].send(request)
      )
  }
}
