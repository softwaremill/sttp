package sttp.client.httpclient

import sttp.client._
import _root_.zio._
import _root_.zio.blocking.Blocking
import sttp.capabilities.zio.BlockingZioStreams
import sttp.capabilities.{Effect, WebSockets}
import sttp.client.impl.zio.{ExtendEnv, SttpClientStubbingBase}

package object zio {

  type BlockingTask[A] = RIO[Blocking, A]

  /**
    * ZIO-environment service definition, which is an SttpBackend.
    */
  type SttpClient = Has[SttpClient.Service]
  type SttpClientStubbing = Has[SttpClientStubbing.Service]

  object SttpClient {

    type Service = SttpBackend[BlockingTask, BlockingZioStreams with WebSockets]

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
      ZIO.accessM(env => env.get[Service].send(request))

    /**
      * A variant of [[send]] which allows the effects that are part of the response handling specification (when
      * using websockets or resource-safe streaming) to use an `R` environment.
      */
    def sendR[T, R](
        request: Request[T, Effect[RIO[Blocking with R, *]] with BlockingZioStreams with WebSockets]
    ): RIO[SttpClient with Blocking with R, Response[T]] =
      ZIO.accessM(env => env.get[Service].extendEnv[R].send(request))
  }

  object SttpClientStubbing extends SttpClientStubbingBase[Blocking, BlockingZioStreams with WebSockets] {
    override private[sttp] def serviceTag: Tag[SttpClientStubbing.Service] = implicitly
    override private[sttp] def sttpBackendTag: Tag[SttpClient.Service] = implicitly
  }

  object stubbing {
    import SttpClientStubbing.StubbingWhenRequest

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
