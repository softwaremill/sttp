package com.softwaremill.sttp.http4s

import cats.effect.{ContextShift, IO}
import com.softwaremill.sttp.{MonadError, Request, Response, SttpBackend}
import fs2.Stream

class TestHttp4sBackend(delegate: SttpBackend[IO, Stream[IO, Byte]], doClose: () => Unit)
    extends SttpBackend[IO, Stream[IO, Byte]] {
  override def send[T](request: Request[T, Stream[IO, Byte]]): IO[Response[T]] = delegate.send(request)
  override def responseMonad: MonadError[IO] = delegate.responseMonad
  override def close(): Unit = doClose()
}

object TestHttp4sBackend {
  def apply()(implicit cf: ContextShift[IO]): TestHttp4sBackend = {
    val (backend, doClose) = ExtractFromResource(Http4sBackend.usingDefaultClientBuilder[IO]())
    new TestHttp4sBackend(backend, doClose)
  }
}
