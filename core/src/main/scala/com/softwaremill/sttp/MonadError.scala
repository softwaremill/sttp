package com.softwaremill.sttp

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.higherKinds
import scala.util.Try

trait MonadError[R[_]] {
  def unit[T](t: T): R[T]
  def map[T, T2](fa: R[T], f: T => T2): R[T2]
  def flatMap[T, T2](fa: R[T], f: T => R[T2]): R[T2]
  def error[T](t: Throwable): R[T]

  def flatten[T](ffa: R[R[T]]): R[T] = flatMap[R[T], T](ffa, identity)

  def fromTry[T](t: Try[T]): R[T] = t.fold(error, unit)
}

trait MonadAsyncError[R[_]] extends MonadError[R] {
  def async[T](register: (Either[Throwable, T] => Unit) => Unit): R[T]
}

object IdMonad extends MonadError[Id] {
  override def unit[T](t: T): Id[T] = t
  override def map[T, T2](fa: Id[T], f: (T) => T2): Id[T2] = f(fa)
  override def flatMap[T, T2](fa: Id[T], f: (T) => Id[T2]): Id[T2] = f(fa)
  override def error[T](t: Throwable): Id[T] = throw t
}

class FutureMonad(implicit ec: ExecutionContext)
    extends MonadAsyncError[Future] {

  override def unit[T](t: T): Future[T] = Future.successful(t)
  override def map[T, T2](fa: Future[T], f: (T) => T2): Future[T2] = fa.map(f)
  override def flatMap[T, T2](fa: Future[T], f: (T) => Future[T2]): Future[T2] =
    fa.flatMap(f)
  override def error[T](t: Throwable): Future[T] = Future.failed(t)

  override def async[T](
      register: ((Either[Throwable, T]) => Unit) => Unit): Future[T] = {
    val p = Promise[T]()
    register {
      case Left(t)  => p.failure(t)
      case Right(t) => p.success(t)
    }
    p.future
  }
}
