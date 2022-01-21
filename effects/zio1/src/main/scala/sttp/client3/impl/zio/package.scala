package sttp.client3.impl

import sttp.capabilities.Effect
import sttp.client3.monad.{FunctionK, MapEffect}
import sttp.client3.{Identity, Request, Response, SttpBackend}
import sttp.monad.MonadError
import _root_.zio.{RIO, ZIO}

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
                override def apply[A](fa: RIO[R0 with R1, A]): RIO[R0, A] = fa.provide(env)
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
}
