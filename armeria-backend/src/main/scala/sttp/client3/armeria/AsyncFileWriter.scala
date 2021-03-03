package sttp.client3.armeria

import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import io.netty.buffer.ByteBuf
import io.netty.util.concurrent.EventExecutor
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.{Path, StandardOpenOption}
import java.util.Objects.requireNonNull
import java.util.concurrent.CompletableFuture
import org.reactivestreams.{Subscriber, Subscription}

private final class AsyncFileWriter(
    publisher: StreamMessage[HttpData],
    path: Path,
    executor: EventExecutor
) extends Subscriber[HttpData]
    with CompletionHandler[Integer, (ByteBuffer, ByteBuf)] {

  private var subscription: Subscription = _
  private var position: Long = 0L
  private var writing: Boolean = false
  private var closing: Boolean = false

  private val completionFuture: CompletableFuture[Void] = new CompletableFuture()

  {
    val file = path.toFile
    if (!file.exists()) {
      if (file.getParentFile != null) {
        file.getParentFile.mkdirs()
      }
      file.createNewFile()
    }
  }
  private val fileChannel: AsynchronousFileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE)

  // Start subscribing to publisher
  publisher.subscribe(this, executor)

  override def onSubscribe(subscription: Subscription): Unit = {
    requireNonNull(subscription, "subscription")
    this.subscription = subscription
    subscription.request(1)
  }

  override def onNext(httpData: HttpData): Unit = {
    if (httpData.isEmpty) {
      httpData.close()
      subscription.request(1)
    } else {
      val byteBuf: ByteBuf = httpData.byteBuf
      val byteBuffer: ByteBuffer = byteBuf.nioBuffer
      writing = true
      fileChannel.write(byteBuffer, position, (byteBuffer, byteBuf), this)
    }
  }

  override def onError(t: Throwable): Unit = {
    maybeCloseFileChannel(Some(t))
  }

  override def onComplete(): Unit = {
    if (!(writing)) {
      maybeCloseFileChannel(None)
    } else {
      closing = true
    }
  }

  def whenComplete(): CompletableFuture[Void] = completionFuture

  override def completed(result: Integer, attachment: (ByteBuffer, ByteBuf)): Unit = {
    executor.execute(() => {
      if (result > -1) {
        position += result
        val (byteBuffer, byteBuf) = attachment

        if (byteBuffer.hasRemaining()) {
          fileChannel.write(byteBuffer, position, attachment, this)
        } else {
          byteBuf.release()
          writing = false

          if (closing) {
            maybeCloseFileChannel(None)
          } else {
            subscription.request(1)
          }
        }
      } else {
        subscription.cancel()
        maybeCloseFileChannel(Some(new IOException(s"Unexpected exception while writing ${path}: result $result")))
      }
    })
  }

  override def failed(cause: Throwable, attachment: (ByteBuffer, ByteBuf)): Unit = {
    subscription.cancel()
    attachment._2.release()
    maybeCloseFileChannel(Some(cause))
  }

  private def maybeCloseFileChannel(cause: Option[Throwable]): Unit = {
    if (completionFuture.isDone()) {
      // no-op
    } else {
      cause.fold(completionFuture.complete(null)) {
        completionFuture.completeExceptionally
      }

      if (fileChannel.isOpen) {
        try fileChannel.close()
        catch {
          case _: IOException =>
        }
      }
    }
  }
}
