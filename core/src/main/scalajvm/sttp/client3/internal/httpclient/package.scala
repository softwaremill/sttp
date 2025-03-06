package sttp.client3.internal

import java.util.concurrent.Flow.Publisher

package object httpclient {
  private[client3] def cancelPublisher[T](p: Publisher[T]): Unit =
    p.subscribe(new java.util.concurrent.Flow.Subscriber[T] {
      override def onSubscribe(s: java.util.concurrent.Flow.Subscription): Unit = s.cancel()
      override def onNext(t: T): Unit = ()
      override def onError(t: Throwable): Unit = ()
      override def onComplete(): Unit = ()
    })
}
