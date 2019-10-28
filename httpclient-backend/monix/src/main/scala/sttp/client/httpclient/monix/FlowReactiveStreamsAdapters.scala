package sttp.client.httpclient.monix

import java.util.concurrent.Flow

import org.reactivestreams.{Publisher, Subscriber, Subscription}

private[monix] final class ReactivePublisherJavaAdapter[T](publisher: Publisher[T]) extends Flow.Publisher[T] {
  override def subscribe(subscriber: Flow.Subscriber[_ >: T]): Unit = {
    publisher.subscribe(new JavaSubscriberReactiveAdapter[T](subscriber))
  }
}

private[monix] final class JavaSubscriberReactiveAdapter[T](subscriber: Flow.Subscriber[_ >: T]) extends Subscriber[T] {
  override def onSubscribe(s: Subscription): Unit = subscriber.onSubscribe(new ReactiveSubscriptionJavaAdapter(s))
  override def onNext(t: T): Unit = subscriber.onNext(t)
  override def onError(t: Throwable): Unit = subscriber.onError(t)
  override def onComplete(): Unit = subscriber.onComplete()
}

private[monix] final class ReactiveSubscriptionJavaAdapter(subscription: Subscription) extends Flow.Subscription {
  override def request(l: Long): Unit = subscription.request(l)
  override def cancel(): Unit = subscription.cancel()
}
