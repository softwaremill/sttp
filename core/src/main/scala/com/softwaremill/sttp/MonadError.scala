package com.softwaremill.sttp

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

trait MonadError[R[_]] {
  def unit[T](t: T): R[T]
  def map[T, T2](fa: R[T])(f: T => T2): R[T2]
  def flatMap[T, T2](fa: R[T])(f: T => R[T2]): R[T2]

  def error[T](t: Throwable): R[T]
  protected def handleWrappedError[T](rt: R[T])(h: PartialFunction[Throwable, R[T]]): R[T]
  def handleError[T](rt: => R[T])(h: PartialFunction[Throwable, R[T]]): R[T] = {
    Try(rt) match {
      case Success(v)                     => handleWrappedError(v)(h)
      case Failure(e) if h.isDefinedAt(e) => h(e)
      case Failure(e)                     => error(e)
    }
  }

  def flatten[T](ffa: R[R[T]]): R[T] = flatMap[R[T], T](ffa)(identity)

  def fromTry[T](t: Try[T]): R[T] = t match {
    case Success(v) => unit(v)
    case Failure(e) => error(e)
  }
}

trait MonadAsyncError[R[_]] extends MonadError[R] {
  def async[T](register: (Either[Throwable, T] => Unit) => Unit): R[T]
}

object syntax {

  implicit final class MonadErrorOps[R[_], A](val r: R[A]) extends AnyVal {
    def map[B](f: A => B)(implicit ME: MonadError[R]): R[B] = ME.map(r)(f)
    def flatMap[B](f: A => R[B])(implicit ME: MonadError[R]): R[B] =
      ME.flatMap(r)(f)
  }
}

object IdMonad extends MonadError[Id] {
  override def unit[T](t: T): Id[T] = t
  override def map[T, T2](fa: Id[T])(f: (T) => T2): Id[T2] = f(fa)
  override def flatMap[T, T2](fa: Id[T])(f: (T) => Id[T2]): Id[T2] = f(fa)

  override def error[T](t: Throwable): Id[T] = throw t
  override protected def handleWrappedError[T](rt: Id[T])(h: PartialFunction[Throwable, Id[T]]): Id[T] = rt
}
object TryMonad extends MonadError[Try] {
  override def unit[T](t: T): Try[T] = Success(t)
  override def map[T, T2](fa: Try[T])(f: (T) => T2): Try[T2] = fa.map(f)
  override def flatMap[T, T2](fa: Try[T])(f: (T) => Try[T2]): Try[T2] =
    fa.flatMap(f)

  override def error[T](t: Throwable): Try[T] = Failure(t)
  override protected def handleWrappedError[T](rt: Try[T])(h: PartialFunction[Throwable, Try[T]]): Try[T] =
    rt.recoverWith(h)
}
class FutureMonad(implicit ec: ExecutionContext) extends MonadAsyncError[Future] {

  override def unit[T](t: T): Future[T] = Future.successful(t)
  override def map[T, T2](fa: Future[T])(f: (T) => T2): Future[T2] = fa.map(f)
  override def flatMap[T, T2](fa: Future[T])(f: (T) => Future[T2]): Future[T2] =
    fa.flatMap(f)

  override def error[T](t: Throwable): Future[T] = Future.failed(t)
  override protected def handleWrappedError[T](rt: Future[T])(h: PartialFunction[Throwable, Future[T]]): Future[T] =
    rt.recoverWith(h)

  override def async[T](register: ((Either[Throwable, T]) => Unit) => Unit): Future[T] = {
    val p = Promise[T]()
    register {
      case Left(t)  => p.failure(t)
      case Right(t) => p.success(t)
    }
    p.future
  }
}
