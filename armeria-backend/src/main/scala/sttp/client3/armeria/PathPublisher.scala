package sttp.client3.armeria

import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.internal.common.stream.NoopSubscription
import io.netty.buffer.{ByteBuf, ByteBufAllocator}
import java.io.IOException
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.{Path, StandardOpenOption}
import java.util.Objects.requireNonNull
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

private final class PathPublisher(path: Path, bufferSize: Int) extends Publisher[HttpData] {

  // TODO(ikhoon): Remove this class once https://github.com/line/armeria/pull/3344 is merged

  private val subscribed = new AtomicBoolean()

  override def subscribe(subscriber: Subscriber[_ >: HttpData]): Unit = {
    requireNonNull(subscriber, "subscriber")
    if (!subscribed.compareAndSet(false, true)) {
      subscriber.onSubscribe(NoopSubscription.get())
      subscriber.onError(new IllegalStateException("Only single subscriber is allowed!"))
    } else {
      try {
        val fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ)
        if (fileChannel.size == 0) {
          subscriber.onSubscribe(NoopSubscription.get())
          subscriber.onComplete()
        } else {
          subscriber.onSubscribe(new PathSubscription(fileChannel, subscriber, bufferSize))
        }
      } catch {
        case e: IOException =>
          subscriber.onSubscribe(NoopSubscription.get())
          subscriber.onError(e)
      }
    }
  }
}

final class PathSubscription(
    fileChannel: AsynchronousFileChannel,
    downstream: Subscriber[_ >: HttpData],
    bufferSize: Int
) extends Subscription
    with CompletionHandler[Integer, ByteBuf] {

  // Updated via requestedUpdater
  private val requested = new AtomicLong()
  // Updated via positionUpdater
  private val position = new AtomicLong()
  // Updated via readingUpdater
  private val reading = new AtomicBoolean()
  // Updated via closedUpdater
  private val cancelled = new AtomicBoolean()

  override def request(n: Long): Unit = {
    if (n <= 0L) {
      cancel()
      downstream.onError(
        new IllegalArgumentException("Rule §3.9 violated: non-positive subscription requests are forbidden.")
      )
    } else {
      val oldRequested = requested.getAndAdd(n)
      if (oldRequested == 0) {
        // No reading in progress
        read()
      }
    }
  }

  private def read(): Unit = {
    if (requested.get() > 0 && !cancelled.get() && reading.compareAndSet(false, true)) {
      requested.getAndDecrement()
      val buffer = ByteBufAllocator.DEFAULT.buffer(bufferSize)
      fileChannel.read(buffer.nioBuffer(0, bufferSize), position.get(), buffer, this)
    }
  }

  override def cancel(): Unit = {
    if (cancelled.compareAndSet(false, true)) {
      if (!reading.get()) {
        maybeCloseFileChannel()
      }
    }
  }

  override def completed(result: Integer, byteBuf: ByteBuf): Unit = {
    if (cancelled.get()) {
      byteBuf.release()
      maybeCloseFileChannel()
    } else if (result > -1) {
      position.getAndAdd(result.longValue())
      byteBuf.writerIndex(result)
      downstream.onNext(HttpData.wrap(byteBuf))
      reading.set(false)
      read()
    } else {
      byteBuf.release()
      maybeCloseFileChannel()
      if (cancelled.compareAndSet(false, true)) {
        downstream.onComplete()
      }
    }
  }

  override def failed(ex: Throwable, byteBuf: ByteBuf): Unit = {
    byteBuf.release()
    maybeCloseFileChannel()
    if (cancelled.compareAndSet(false, true)) {
      downstream.onError(ex)
    }
  }

  private def maybeCloseFileChannel(): Unit = {
    if (fileChannel.isOpen()) {
      try fileChannel.close()
      catch {
        case _: IOException =>
      }
    }
  }
}
