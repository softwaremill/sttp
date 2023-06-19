package sttp.client4.impl.zio

import sttp.client4.monad.{FunctionK, MapEffect}
import sttp.client4._
import sttp.monad.MonadError
import _root_.zio.{RIO, ZIO}
import sttp.capabilities.Effect

private abstract class ExtendedEnvBackend[R0, R1, P](delegate: GenericBackend[RIO[R0, *], P])
    extends GenericBackend[RIO[R0 with R1, *], P] {
  override def send[T](
      request: GenericRequest[T, P with Effect[RIO[R0 with R1, *]]]
  ): RIO[R0 with R1, Response[T]] =
    for {
      env <- ZIO.environment[R0 with R1]
      mappedRequest = MapEffect[RIO[R0 with R1, *], RIO[R0, *], T, P](
        request,
        new FunctionK[RIO[R0 with R1, *], RIO[R0, *]] {
          override def apply[A](fa: => RIO[R0 with R1, A]): RIO[R0, A] = fa.provideEnvironment(env)
        },
        new FunctionK[RIO[R0, *], RIO[R0 with R1, *]] {
          override def apply[A](fa: => RIO[R0, A]): RIO[R0 with R1, A] = fa
        },
        monad,
        delegate.monad
      )
      resp <- delegate.send(mappedRequest)
    } yield resp

  override def close(): RIO[R0 with R1, Unit] = delegate.close()

  override val monad: MonadError[RIO[R0 with R1, *]] = new RIOMonadAsyncError[R0 with R1]
}
