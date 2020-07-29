package sttp.client

import sttp.client.monad.{EitherMonad, FunctionK, MapEffect, MonadError}

import scala.util.control.NonFatal

/** A synchronous backend that safely wraps [[SttpBackend]] exceptions in `Either[Throwable, *]`'s
  *
  * @param delegate A synchronous `SttpBackend` which to which this backend forwards all requests
  * @tparam P TODO
  */
class EitherBackend[P](delegate: SttpBackend[Identity, P]) extends SttpBackend[Either[Throwable, *], P] {
  override def send[T, R >: P with Effect[Either[Throwable, *]]](
      request: Request[T, R]
  ): Either[Throwable, Response[T]] =
    doTry(
      delegate.send(
        MapEffect[Either[Throwable, *], Identity, Identity, T, P](
          request: Request[T, P with Effect[Either[Throwable, *]]],
          eitherToId,
          idToEither,
          responseMonad,
          delegate.responseMonad
        )
      )
    )

  override def close(): Either[Throwable, Unit] = doTry(delegate.close())

  private def doTry[T](t: => T): Either[Throwable, T] = {
    try Right(t)
    catch {
      case NonFatal(e) => Left(e)
    }
  }

  override def responseMonad: MonadError[Either[Throwable, *]] = EitherMonad

  private val eitherToId: FunctionK[Either[Throwable, *], Identity] =
    new FunctionK[Either[Throwable, *], Identity] {
      override def apply[A](fa: Either[Throwable, A]): Identity[A] =
        fa match {
          case Left(e)  => throw e
          case Right(v) => v
        }
    }

  private val idToEither: FunctionK[Identity, Either[Throwable, *]] =
    new FunctionK[Identity, Either[Throwable, *]] {
      override def apply[A](fa: Identity[A]): Either[Throwable, A] = Right(fa)
    }
}
