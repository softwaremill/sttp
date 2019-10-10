package sttp.client.http4s

import cats.effect.{ContextShift, IO}
import sttp.client.monad.MonadError
import sttp.client.{NothingT, Request, Response, SttpBackend, WebSocketResponse}
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

class TestHttp4sBackend(delegate: SttpBackend[IO, Stream[IO, Byte], NothingT], doClose: () => Unit)
    extends SttpBackend[IO, Stream[IO, Byte], NothingT] {
  override def send[T](request: Request[T, Stream[IO, Byte]]): IO[Response[T]] = delegate.send(request)
  override def openWebsocket[T, WS_RESULT](
      request: Request[T, Stream[IO, Byte]],
      handler: NothingT[WS_RESULT]
  ): IO[WebSocketResponse[WS_RESULT]] = delegate.openWebsocket(request, handler)
  override def responseMonad: MonadError[IO] = delegate.responseMonad
  override def close(): IO[Unit] = IO(doClose())
}

object TestHttp4sBackend {
  def apply()(implicit cf: ContextShift[IO]): TestHttp4sBackend = {
    val blazeClientBuilder = BlazeClientBuilder[IO](ExecutionContext.Implicits.global)
    val (backend, doClose) = ExtractFromResource(Http4sBackend.usingClientBuilder(blazeClientBuilder))
    new TestHttp4sBackend(backend, doClose)
  }
}
