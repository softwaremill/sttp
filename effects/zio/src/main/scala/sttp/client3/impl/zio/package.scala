package sttp.client3.impl

import _root_.zio._
import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.{Effect, WebSockets}
import sttp.client3.monad.{FunctionK, MapEffect}
import sttp.client3.{Identity, Request, RequestT, Response, SttpBackend, SttpClientException}
import sttp.monad.MonadError

package object zio {
  implicit class ExtendEnv[R0, P](delegate: SttpBackend[RIO[R0, *], P]) {
    def extendEnv[R1]: SttpBackend[RIO[R0 with R1, *], P] =
      new SttpBackend[RIO[R0 with R1, *], P] {
        override def send[T, R >: P with Effect[RIO[R0 with R1, *]]](
            request: Request[T, R]
        ): RIO[R0 with R1, Response[T]] =
          for {
            env <- ZIO.environment[R0 with R1]
            mappedRequest = MapEffect[RIO[R0 with R1, *], RIO[R0, *], Identity, T, P](
              request,
              new FunctionK[RIO[R0 with R1, *], RIO[R0, *]] {
                override def apply[A](fa: RIO[R0 with R1, A]): RIO[R0, A] = fa.provideEnvironment(env)
              },
              new FunctionK[RIO[R0, *], RIO[R0 with R1, *]] {
                override def apply[A](fa: RIO[R0, A]): RIO[R0 with R1, A] = fa
              },
              responseMonad,
              delegate.responseMonad
            )
            resp <- delegate.send(mappedRequest)
          } yield resp

        override def close(): RIO[R0 with R1, Unit] = delegate.close()

        override val responseMonad: MonadError[RIO[R0 with R1, *]] = new RIOMonadAsyncError[R0 with R1]
      }
  }

  /** ZIO-environment service definition, which is an SttpBackend. */
  type SttpClientWebSockets = SttpBackend[Task, ZioStreams with WebSockets]
  type SttpClientStubbingWebSockets = SttpClientStubbingWebSockets.SttpClientStubbing

  type SttpClient = SttpBackend[Task, ZioStreams]
  type SttpClientStubbing = SttpClientStubbing.SttpClientStubbing

  /** Sends the request. Only requests for which the method & URI are specified can be sent.
    *
    * @return
    *   An effect resulting in a [[Response]], containing the body, deserialized as specified by the request (see
    *   [[RequestT.response]]), if the request was successful (1xx, 2xx, 3xx response codes), or if there was a
    *   protocol-level failure (4xx, 5xx response codes).
    *
    * A failed effect, if an exception occurred when connecting to the target host, writing the request or reading the
    * response.
    *
    * Known exceptions are converted to one of [[SttpClientException]]. Other exceptions are kept unchanged.
    */
  def sendWebSockets[T](
      request: Request[T, ZioStreams with Effect[Task] with WebSockets]
  ): RIO[SttpClientWebSockets, Response[T]] =
    ZIO.environmentWithZIO(env => env.get[SttpClientWebSockets].send(request))

  def send[T](
      request: Request[T, Effect[Task] with ZioStreams]
  ): ZIO[SttpClient, Throwable, Response[T]] =
    ZIO.environmentWithZIO(env => env.get[SttpClient].send(request))

  /** A variant of `send` which allows the effects that are part of the response handling specification to use an `R`
    * environment.
    */

  def sendRWebSockets[T, R](
      request: Request[T, Effect[RIO[R, *]] with ZioStreams with WebSockets]
  ): RIO[SttpClientWebSockets with R, Response[T]] =
    ZIO.environmentWithZIO(env => env.get[SttpClientWebSockets].extendEnv[R].send(request))

  def sendR[T, R](
      request: Request[T, Effect[RIO[R, *]] with ZioStreams]
  ): ZIO[SttpClient with R, Throwable, Response[T]] =
    ZIO.environmentWithZIO(env => env.get[SttpClient].extendEnv[R].send(request))

  object SttpClientStubbingWebSockets extends SttpClientStubbingBase[Any, ZioStreams with WebSockets] {
    override private[sttp] def serviceTag: Tag[SttpClientStubbingWebSockets.SttpClientStubbing] = implicitly
    override private[sttp] def sttpBackendTag: Tag[SttpClientWebSockets] = implicitly
  }

  object SttpClientStubbing extends SttpClientStubbingBase[Any, ZioStreams] {
    override private[sttp] def serviceTag: Tag[SttpClientStubbing.SttpClientStubbing] = implicitly
    override private[sttp] def sttpBackendTag: Tag[SttpClient] = implicitly
  }

  object stubbingWebSockets {
    import SttpClientStubbingWebSockets.StubbingWhenRequest

    def whenRequestMatches(p: Request[_, _] => Boolean): StubbingWhenRequest =
      StubbingWhenRequest(p)

    val whenAnyRequest: StubbingWhenRequest =
      StubbingWhenRequest(_ => true)

    def whenRequestMatchesPartial(
        partial: PartialFunction[Request[_, _], Response[_]]
    ): URIO[SttpClientStubbingWebSockets, Unit] =
      ZIO.environmentWithZIO(_.get.whenRequestMatchesPartial(partial))
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
      ZIO.environmentWithZIO(_.get.whenRequestMatchesPartial(partial))
  }
}
