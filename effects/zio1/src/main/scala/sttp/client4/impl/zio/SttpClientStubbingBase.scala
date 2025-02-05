package sttp.client4.impl.zio

import sttp.capabilities.Effect
import sttp.client4.testing._
import sttp.client4._
import sttp.model.StatusCode
import sttp.monad.MonadError
import zio.{Has, RIO, Ref, Tag, UIO, URIO, ZLayer}
import sttp.capabilities.WebSockets

trait AbstractClientStubbing[R, P] {
  type SttpClientStubbing = Has[Service]
  type Backend <: GenericBackend[RIO[R, *], P]
  type BackendStub <: AbstractBackendStub[RIO[R, *], P] { type Self = BackendStub }

  // the tag as viewed by the implementing object. Needs to be passed explicitly, otherwise Has[] breaks.
  private[sttp] def serviceTag: Tag[Service]
  private[sttp] def sttpBackendTag: Tag[Backend]

  val monad = new RIOMonadAsyncError[R]
  protected def backendStub: BackendStub
  protected def proxy(stub: Ref[BackendStub]): Backend

  trait Service {
    def whenRequestMatchesPartial(
        partial: PartialFunction[GenericRequest[_, _], Response[StubBody]]
    ): URIO[SttpClientStubbing, Unit]

    private[zio] def update(f: BackendStub => BackendStub): UIO[Unit]
  }

  private[sttp] class StubWrapper(stub: Ref[BackendStub]) extends Service {
    override def whenRequestMatchesPartial(
        partial: PartialFunction[GenericRequest[_, _], Response[StubBody]]
    ): URIO[SttpClientStubbing, Unit] =
      update(_.whenRequestMatchesPartial(partial))

    override private[zio] def update(f: BackendStub => BackendStub) = stub.update(f)
  }

  case class StubbingWhenRequest private[sttp] (p: GenericRequest[_, _] => Boolean) {
    implicit val _serviceTag: Tag[Service] = serviceTag
    val thenRespondOk: URIO[SttpClientStubbing, Unit] =
      whenRequest(_.whenRequestMatches(p).thenRespondOk(): BackendStub)

    def thenRespondNotFound(): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.whenRequestMatches(p).thenRespondNotFound())

    def thenRespondServerError(): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.whenRequestMatches(p).thenRespondServerError())

    def thenRespondWithCode(status: StatusCode): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.whenRequestMatches(p).thenRespondWithCode(status))

    def thenRespondAdjust[T](body: T): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.whenRequestMatches(p).thenRespondAdjust(body))

    def thenRespondExact[T](body: T): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.whenRequestMatches(p).thenRespondExact(body))

    def thenRespond[T](resp: => Response[StubBody]): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.whenRequestMatches(p).thenRespond(resp))

    def thenRespondCyclic[T](responses: Response[StubBody]*): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.whenRequestMatches(p).thenRespondCyclic(responses: _*))

    def thenRespondF(resp: => RIO[R, Response[StubBody]]): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.whenRequestMatches(p).thenRespondF(resp))

    def thenRespondF(resp: GenericRequest[_, _] => RIO[R, Response[StubBody]]): URIO[SttpClientStubbing, Unit] =
      whenRequest(_.whenRequestMatches(p).thenRespondF(resp))

    private def whenRequest(f: BackendStub => BackendStub): URIO[SttpClientStubbing, Unit] =
      URIO.serviceWith(_.update(f))
  }

  val layer: ZLayer[Any, Nothing, Has[Service] with Has[Backend]] = {
    implicit val _serviceTag: Tag[Service] = serviceTag
    implicit val _backendTag: Tag[Backend] = sttpBackendTag

    val composed = for {
      stub <- Ref.make(backendStub)
      stubber = new StubWrapper(stub)
    } yield Has.allOf[Service, Backend](stubber, proxy(stub))

    composed.toLayerMany
  }
}

trait StreamClientStubbing[R, P] extends AbstractClientStubbing[R, P] {
  type Backend = StreamBackend[RIO[R, *], P]
  type BackendStub = StreamBackendStub[RIO[R, *], P]

  def backendStub: StreamBackendStub[RIO[R, *], P] = StreamBackendStub(monad)
  def proxy(stub: Ref[StreamBackendStub[RIO[R, *], P]]): StreamBackend[RIO[R, *], P] = new StreamBackend[RIO[R, *], P] {
    def send[T](request: GenericRequest[T, P with Effect[RIO[R, *]]]): RIO[R, Response[T]] =
      stub.get >>= (_.send(request))
    def close(): RIO[R, Unit] =
      stub.get >>= (_.close())

    def monad: MonadError[RIO[R, *]] = monad
  }
}

trait WebSocketStreamClientStubbing[R, P] extends AbstractClientStubbing[R, P with WebSockets] {
  type Backend = WebSocketStreamBackend[RIO[R, *], P]
  type BackendStub = WebSocketStreamBackendStub[RIO[R, *], P]

  def backendStub: WebSocketStreamBackendStub[RIO[R, *], P] = WebSocketStreamBackendStub(monad)

  def proxy(stub: Ref[WebSocketStreamBackendStub[RIO[R, *], P]]): WebSocketStreamBackend[RIO[R, *], P] =
    new WebSocketStreamBackend[RIO[R, *], P] {
      def send[T](
          request: GenericRequest[T, P with WebSockets with Effect[RIO[R, *]]]
      ): RIO[R, Response[T]] =
        stub.get >>= (_.send(request))
      def close(): RIO[R, Unit] =
        stub.get >>= (_.close())

      def monad: MonadError[RIO[R, *]] = monad
    }
}
