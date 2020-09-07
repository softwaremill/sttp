package sttp.client.impl.zio

import sttp.capabilities.Effect
import sttp.client.testing.SttpBackendStub
import sttp.client.{Request, Response, SttpBackend}
import sttp.model.StatusCode
import sttp.monad.MonadError
import zio.{Has, Queue, RIO, Ref, Tag, UIO, URIO, ZLayer}

trait SttpClientStubbingBase[R, P] {

  type SttpClientStubbing = Has[Service]
  // the tag as viewed by the implementing object. Needs to be passed explicitly, otherwise Has[] breaks.
  private[sttp] def serviceTag: Tag[Service]
  private[sttp] def sttpBackendTag: Tag[SttpBackend[RIO[R, *], P]]

  trait Service {
    def whenRequestMatchesPartial(partial: PartialFunction[Request[_, _], Response[_]]): URIO[SttpClientStubbing, Unit]

    private[zio] def update(f: SttpBackendStub[RIO[R, *], P] => SttpBackendStub[RIO[R, *], P]): UIO[Unit]
  }

  private[sttp] class StubWrapper(stub: Ref[SttpBackendStub[RIO[R, *], P]]) extends Service {
    override def whenRequestMatchesPartial(
        partial: PartialFunction[Request[_, _], Response[_]]
    ): URIO[SttpClientStubbing, Unit] =
      update(_.whenRequestMatchesPartial(partial))

    override private[zio] def update(f: SttpBackendStub[RIO[R, *], P] => SttpBackendStub[RIO[R, *], P]) = stub.update(f)
  }

  case class StubbingWhenRequest private[sttp] (p: Request[_, _] => Boolean) {
    implicit val _serviceTag: Tag[Service] = serviceTag
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

    def thenRespondWrapped(resp: => RIO[R, Response[_]]): URIO[SttpClientStubbing, Unit] = {
      val m: PartialFunction[Request[_, _], RIO[R, Response[_]]] = {
        case r if p(r) => resp
      }
      URIO.accessM(_.get.update(_.whenRequestMatches(p).thenRespondF(m)))
    }

    def thenRespondWrapped(resp: Request[_, _] => RIO[R, Response[_]]): URIO[SttpClientStubbing, Unit] = {
      val m: PartialFunction[Request[_, _], RIO[R, Response[_]]] = {
        case r if p(r) => resp(r)
      }
      URIO.accessM(_.get.update(_.whenRequestMatches(p).thenRespondF(m)))
    }
  }

  val layer: ZLayer[Any, Nothing, Has[Service] with Has[SttpBackend[RIO[R, *], P]]] = {
    val monad = new RIOMonadAsyncError[R]
    implicit val _serviceTag: Tag[Service] = serviceTag
    implicit val _backendTag: Tag[SttpBackend[RIO[R, *], P]] = sttpBackendTag
    ZLayer.fromEffectMany(for {
      stub <- Ref.make(SttpBackendStub[RIO[R, *], P](monad))
      stubber = new StubWrapper(stub)
      proxy = new SttpBackend[RIO[R, *], P] {
        override def send[T, RR >: P with Effect[RIO[R, *]]](request: Request[T, RR]): RIO[R, Response[T]] =
          stub.get >>= (_.send(request))

        override def close(): RIO[R, Unit] =
          stub.get >>= (_.close())

        override def responseMonad: MonadError[RIO[R, *]] = monad
      }
    } yield Has.allOf[Service, SttpBackend[RIO[R, *], P]](stubber, proxy))
  }
}
