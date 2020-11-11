package sttp.client3.asynchttpclient

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.collection.JavaConverters._

private[asynchttpclient] object EmptyPublisher extends Publisher[ByteBuffer] {
  override def subscribe(s: Subscriber[_ >: ByteBuffer]): Unit = {
    s.onComplete()
  }
}

// based on org.asynchttpclient.request.body.generator.ReactiveStreamsBodyGenerator.SimpleSubscriber
private[asynchttpclient] class SimpleSubscriber(success: ByteBuffer => Unit, error: Throwable => Unit)
    extends Subscriber[ByteBuffer] {
  // a pair of values: (is cancelled, current subscription)
  private val subscription = new AtomicReference[(Boolean, Subscription)]((false, null))
  private val chunks = new ConcurrentLinkedQueue[Array[Byte]]()
  private var size = 0

  override def onSubscribe(s: Subscription): Unit = {
    assert(s != null)

    // The following can be safely run multiple times, as cancel() is idempotent
    val result = subscription.updateAndGet(new UnaryOperator[(Boolean, Subscription)] {
      override def apply(current: (Boolean, Subscription)): (Boolean, Subscription) = {
        // If someone has made a mistake and added this Subscriber multiple times, let's handle it gracefully
        if (current._2 != null) {
          current._2.cancel() // Cancel the additional subscription
        }

        if (current._1) { // already cancelled
          s.cancel()
          (true, null)
        } else { // happy path
          (false, s)
        }
      }
    })

    if (result._2 != null) {
      result._2.request(Long.MaxValue) // not cancelled, we can request data
    }
  }

  override def onNext(b: ByteBuffer): Unit = {
    assert(b != null)
    val a = b.array()
    size += a.length
    chunks.add(a)
  }

  override def onError(t: Throwable): Unit = {
    assert(t != null)
    chunks.clear()
    error(t)
  }

  override def onComplete(): Unit = {
    val result = ByteBuffer.allocate(size)
    chunks.asScala.foreach(result.put)
    chunks.clear()
    success(result)
  }

  def cancel(): Unit = {
    // subscription.cancel is idempotent:
    // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#specification
    // so the following can be safely retried
    subscription.updateAndGet(new UnaryOperator[(Boolean, Subscription)] {
      override def apply(current: (Boolean, Subscription)): (Boolean, Subscription) = {
        if (current._2 != null) current._2.cancel()
        (true, null)
      }
    })
  }
}

/** A subscriber which does its best to signal that it's not interested in the data being sent.
  */
private[asynchttpclient] class IgnoreSubscriber(success: () => Unit, error: Throwable => Unit)
    extends Subscriber[ByteBuffer] {
  override def onSubscribe(s: Subscription): Unit = {
    s.cancel()
    success()
  }

  override def onNext(b: ByteBuffer): Unit = {
    // ignore
  }

  override def onError(t: Throwable): Unit = {
    error(t)
  }

  override def onComplete(): Unit = {
    success()
  }
}

private[asynchttpclient] class SingleElementPublisher[T](v: T) extends Publisher[T] {
  override def subscribe(s: Subscriber[_ >: T]): Unit = {
    s.onSubscribe(new Subscription {
      override def request(n: Long): Unit =
        if (n > 0) {
          s.onNext(v)
          s.onComplete()
        }

      override def cancel(): Unit = {}
    })
  }
}
