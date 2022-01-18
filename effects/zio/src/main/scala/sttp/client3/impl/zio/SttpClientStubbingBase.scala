package sttp.client3.impl.zio

import sttp.capabilities.Effect
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Request, Response, SttpBackend}
import sttp.model.StatusCode
import sttp.monad.MonadError
import zio.{RIO, Ref, Tag, UIO, URIO, ZEnvironment, ZLayer}

trait SttpClientStubbingBase[R, P] {

  // the tag as viewed by the implementing object. Needs to be passed explicitly, otherwise env lookup breaks.
  private[sttp] def serviceTag: Tag[SttpClientStubbing]
  private[sttp] def sttpBackendTag: Tag[SttpBackend[RIO[R, *], P]]

  trait SttpClientStubbing {
    def whenRequestMatchesPartial(partial: PartialFunction[Request[_, _], Response[_]]): URIO[SttpClientStubbing, Unit]

    private[zio] def update(f: SttpBackendStub[RIO[R, *], P] => SttpBackendStub[RIO[R, *], P]): UIO[Unit]
  }

  private[sttp] class StubWrapper(stub: Ref[SttpBackendStub[RIO[R, *], P]]) extends SttpClientStubbing {
    override def whenRequestMatchesPartial(
        partial: PartialFunction[Request[_, _], Response[_]]
    ): URIO[SttpClientStubbing, Unit] =
      update(_.whenRequestMatchesPartial(partial))

    override private[zio] def update(f: SttpBackendStub[RIO[R, *], P] => SttpBackendStub[RIO[R, *], P]) = stub.update(f)
  }

  case class StubbingWhenRequest private[sttp] (p: Request[_, _] => Boolean) {
    implicit val _serviceTag: Tag[SttpClientStubbing] = serviceTag

    val thenRespondOk: URIO[SttpClientStubbing, Unit] =
      whenRequest(_.thenRespondOk())

    def thenRespondNotFound(): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.thenRespondNotFound())

    def thenRespondServerError(): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.thenRespondServerError())

    def thenRespondWithCode(status: StatusCode, msg: String = ""): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.thenRespondWithCode(status, msg))

    def thenRespond[T](body: T): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.thenRespond(body))

    def thenRespond[T](resp: => Response[T]): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.thenRespond(resp))

    def thenRespondCyclic[T](bodies: T*): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.thenRespondCyclic(bodies: _*))

    def thenRespondCyclicResponses[T](responses: Response[T]*): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.thenRespondCyclicResponses(responses: _*))

    def thenRespondF(resp: => RIO[R, Response[_]]): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.thenRespondF(resp))

    def thenRespondF(resp: Request[_, _] => RIO[R, Response[_]]): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.thenRespondF(resp))

    private def whenRequest(
        f: SttpBackendStub[RIO[R, *], P]#WhenRequest => SttpBackendStub[RIO[R, *], P]
    ): URIO[SttpClientStubbing, Unit] =
      URIO.serviceWithZIO((_: SttpClientStubbing).update(stub => f(stub.whenRequestMatches(p))))
  }

  val layer: ZLayer[Any, Nothing, SttpClientStubbing with SttpBackend[RIO[R, *], P]] = {
    val monad = new RIOMonadAsyncError[R]
    implicit val _serviceTag: Tag[SttpClientStubbing] = serviceTag
    implicit val _backendTag: Tag[SttpBackend[RIO[R, *], P]] = sttpBackendTag

    val composed = for {
      stub <- Ref.make(SttpBackendStub[RIO[R, *], P](monad))
      stubber = new StubWrapper(stub): SttpClientStubbing
      proxy = new SttpBackend[RIO[R, *], P] {
        override def send[T, RR >: P with Effect[RIO[R, *]]](request: Request[T, RR]): RIO[R, Response[T]] =
          stub.get >>= (_.send(request))

        override def close(): RIO[R, Unit] =
          stub.get >>= (_.close())

        override def responseMonad: MonadError[RIO[R, *]] = monad
      }: SttpBackend[RIO[R, *], P]
    } yield ZEnvironment(stubber, proxy)

    ZLayer.fromZIOEnvironment(composed)
  }
}
