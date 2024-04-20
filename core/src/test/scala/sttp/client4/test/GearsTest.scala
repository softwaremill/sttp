package sttp.client4.test

import sttp.capabilities.Effect
import sttp.client4.monad.FunctionK
import sttp.client4.{Backend, BackendOptions, DefaultSyncBackend, GenericRequest, Identity, Response, SyncBackend}
import sttp.monad.MonadError

trait Async

@main def test2(): Unit =
  type Deferred[T] = Async ?=> T

  // helper code needed to wrap a sync backend as a gears backend
  object DeferredMonad extends MonadError[Deferred] {
    override def unit[T](t: T): Deferred[T] = t
    override def map[T, T2](fa: Deferred[T])(f: T => T2): Deferred[T2] = f(fa)
    override def flatMap[T, T2](fa: Deferred[T])(f: (T) => Deferred[T2]): Deferred[T2] = f(fa)
    override def error[T](t: Throwable): Deferred[T] = throw t

    override protected def handleWrappedError[T](rt: Deferred[T])(
        h: PartialFunction[Throwable, Deferred[T]]
    ): Deferred[T] = rt

    override def eval[T](t: => T): Deferred[T] = t

    override def ensure[T](f: Deferred[T], e: => Deferred[Unit]): Deferred[T] =
      try f
      finally e
  }

  class DeferredToId(using Async) extends FunctionK[Deferred, Identity]:
    override def apply[A](fa: => Deferred[A]): A = fa

  object IdToDeferred extends FunctionK[Identity, Deferred]:
    override def apply[A](fa: => A): Deferred[A] = fa

  //

  // this implementation simply wraps any other synchronous backend, but we could also provide one which uses the
  // Async capability in some way
  class GearsBackendWrapper(delegate: SyncBackend) extends Backend[Deferred]:
    override def send[T](request: GenericRequest[T, Effect[Deferred]]): Async ?=> Response[T] =
      // delegate.send(MapEffect(request, DeferredToId(), IdToDeferred, DeferredMonad, IdMonad))
      println("SEND!")
      Response.ok(null.asInstanceOf[T])

    override def close(): Deferred[Unit] = delegate.close()
    override def monad: MonadError[Deferred] = DeferredMonad

  type GearsBackend = Backend[Deferred]
  object GearsBackend:
    def apply(options: BackendOptions = BackendOptions.Default): GearsBackend = GearsBackendWrapper(
      DefaultSyncBackend(options)
    )

//  extension [T](request: Request[T])
//    def send(backend: Backend[Deferred])(using Async): Response[T] =
//      backend.send(request)

  //

  import sttp.client4.*
  val b: Backend[Deferred] = GearsBackend()

  given Async = new Async {}
  println("Ac")
  // : Response[Either[String, String]]
  val result = basicRequest.get(uri"https://x.org").body("123").send(b)
  println("B")
  result.body
  println("C")
  result.body
  println("D")
