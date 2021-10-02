package sttp.client3.httpclient

import java.io.InputStream
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
import java.util.stream.{Collector, Collectors}

import org.reactivestreams.{Subscriber, Subscription}
import sttp.client3.httpclient.InputStreamSubscriber._

// based on org.asynchttpclient.request.body.generator.ReactiveStreamsBodyGenerator.SimpleSubscriber
private[httpclient] class InputStreamSubscriber extends Subscriber[java.util.List[ByteBuffer]] {
  // a pair of values: (is cancelled, current subscription)
  private val subscription = new AtomicReference[(Boolean, Subscription)]((false, null))
  private val chunks = new LinkedBlockingQueue[Message]()

  val inputStream: InputStream = new InputStream {
    private var exhausted = false
    private var currentBuffer: Option[ByteBuffer] = None

    override def read(): Int = {
      if (exhausted) {
        -1
      } else {
        val byteRead = currentBuffer match {
          case Some(buffer) if buffer.hasRemaining =>
            buffer.get() & 0xff
          case _ =>
            chunks.take() match {
              case NextItem(buffer) =>
                currentBuffer = Some(buffer)
                buffer.get() & 0xff
              case Error(ex) =>
                throw ex
              case Completed() =>
                exhausted = true
                -1
            }
        }
        byteRead
      }
    }
  }

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

  private val toListCollector: Collector[Message, _, util.List[Message]] = Collectors.toList()
  override def onNext(b: java.util.List[ByteBuffer]): Unit = {
    assert(b != null)
    chunks.addAll(b.stream().map[Message](nextItemMsg(_)).collect(toListCollector))
  }

  override def onError(t: Throwable): Unit = {
    assert(t != null)
    chunks.add(Error(t))
  }

  override def onComplete(): Unit = {
    chunks.add(Completed())
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

object InputStreamSubscriber {
  sealed trait Message

  case class NextItem(payload: ByteBuffer) extends Message
  case class Error(ex: Throwable) extends Message
  case class Completed() extends Message

  def nextItemMsg(payload: ByteBuffer): Message = NextItem(payload)
}
