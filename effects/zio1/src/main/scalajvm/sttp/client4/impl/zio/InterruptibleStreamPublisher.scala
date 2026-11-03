package sttp.client4.impl.zio

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import zio._
import zio.interop.reactivestreams._
import zio.stream.ZStream

import java.util.Objects

private[client4] object InterruptibleStreamPublisher {

  /** Like zio-interop's `toPublisher`, but also interrupts the stream when the subscription is cancelled, running its
    * finalizers. Each subscriber runs the stream from the start.
    */
  // zio-interop only stops requesting elements on cancel, so a stream waiting for its next element never ends
  def apply[A](stream: ZStream[Any, Throwable, A]): UIO[Publisher[A]] =
    ZIO.runtime[Any].map { runtime =>
      new Publisher[A] {
        override def subscribe(subscriber: Subscriber[_ >: A]): Unit = {
          Objects.requireNonNull(subscriber)
          val cancelled = runtime.unsafeRun(Promise.make[Throwable, Unit])
          val publisher = runtime.unsafeRun(stream.interruptWhen(cancelled).toPublisher)
          publisher.subscribe(new Subscriber[A] {
            override def onSubscribe(s: Subscription): Unit =
              subscriber.onSubscribe(new Subscription {
                override def request(n: Long): Unit = s.request(n)
                override def cancel(): Unit = {
                  s.cancel()
                  runtime.unsafeRun(cancelled.succeed(())): Unit
                }
              })
            override def onNext(a: A): Unit = subscriber.onNext(a)
            override def onError(t: Throwable): Unit = subscriber.onError(t)
            override def onComplete(): Unit = subscriber.onComplete()
          })
        }
      }
    }
}
