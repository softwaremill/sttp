package sttp.client.monad

import sttp.client.Identity
import sttp.monad.MonadError

object IdMonad extends MonadError[Identity] {
  override def unit[T](t: T): Identity[T] = t
  override def map[T, T2](fa: Identity[T])(f: (T) => T2): Identity[T2] = f(fa)
  override def flatMap[T, T2](fa: Identity[T])(f: (T) => Identity[T2]): Identity[T2] = f(fa)

  override def error[T](t: Throwable): Identity[T] = throw t
  override protected def handleWrappedError[T](rt: Identity[T])(
      h: PartialFunction[Throwable, Identity[T]]
  ): Identity[T] = rt

  override def eval[T](t: => T): Identity[T] = t

  override def ensure[T](f: Identity[T], e: => Identity[Unit]): Identity[T] =
    try f
    finally e
}
