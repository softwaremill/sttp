package sttp.client4.impl.fs2

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import fs2.Stream

import java.util.Objects
import java.util.concurrent.{Flow, RejectedExecutionException}

private[client4] object StreamFlowPublisher {

  /** A publisher of the stream's elements, which runs the stream once per subscriber using the given dispatcher. */
  // fs2's Stream#toPublisherResource requires a Resource scope, which isn't available when creating a request body
  def apply[F[_]: Async, A](stream: Stream[F, A], dispatcher: Dispatcher[F]): Flow.Publisher[A] =
    new Flow.Publisher[A] {
      override def subscribe(subscriber: Flow.Subscriber[_ >: A]): Unit = {
        Objects.requireNonNull(subscriber)
        try dispatcher.unsafeRunAndForget(stream.subscribe(subscriber.asInstanceOf[Flow.Subscriber[A]]).compile.drain)
        catch {
          // the dispatcher is closed; the spec (rule 1.9) requires signalling the error instead of throwing
          case e @ (_: IllegalStateException | _: RejectedExecutionException) =>
            subscriber.onSubscribe(NoopSubscription)
            subscriber.onError(e)
        }
      }
    }

  private object NoopSubscription extends Flow.Subscription {
    override def request(n: Long): Unit = ()
    override def cancel(): Unit = ()
  }
}
