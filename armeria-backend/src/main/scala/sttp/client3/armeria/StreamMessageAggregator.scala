package sttp.client3.armeria

import com.linecorp.armeria.common.HttpData
import java.util.concurrent.CompletableFuture
import org.reactivestreams.{Subscriber, Subscription}
import scala.collection.mutable

private final class StreamMessageAggregator extends Subscriber[HttpData] {
  private val contentList: mutable.Buffer[HttpData] = new mutable.ArrayBuffer()
  private var contentLength: Int = 0
  private var subscription: Subscription = _

  val future: CompletableFuture[HttpData] = new CompletableFuture()

  override def onSubscribe(s: Subscription): Unit = {
    subscription = s
    s.request(Long.MaxValue)
  }

  override def onNext(o: HttpData): Unit = {
    var added = false
    try {
      if (future.isDone) {
        // no-op
      } else {
        val dataLength = o.length
        if (dataLength > 0) {
          val allowedMaxDataLength = Int.MaxValue - contentLength
          if (dataLength > allowedMaxDataLength) {
            subscription.cancel()
            fail(new IllegalStateException("content length greater than Int.MaxValue"))
            return
          }
          contentList += o
          contentLength += dataLength
          added = true
        }
      }
    } finally {
      if (!added) {
        o.close()
      }
    }
  }

  override def onComplete(): Unit = {
    if (future.isDone) {
      // no-op
    } else {
      val content: HttpData =
        if (contentLength == 0) HttpData.empty()
        else {
          val merged = new Array[Byte](contentLength)
          var offset = 0

          contentList.foreach(data => {
            val dataLength = data.length()
            System.arraycopy(data.array(), 0, merged, offset, dataLength)
            offset += dataLength
          })
          contentList.clear()
          HttpData.wrap(merged)
        }
      val _ = future.complete(content)
    }
  }

  override def onError(t: Throwable): Unit = {
    fail(t)
  }

  private def fail(cause: Throwable): Unit = {
    contentList.foreach(_.close)
    contentList.clear()
    val _ = future.completeExceptionally(cause)
  }
}
