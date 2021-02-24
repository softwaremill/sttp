package sttp.client3

import org.scalajs.dom.window
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.ws.WebSocketTimeoutException
import sttp.ws.WebSocketBufferFull

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Future, Promise}

private[client3] class JSSimpleQueue[T](timeout: FiniteDuration = 1.second) extends SimpleQueue[Future, T] {

  private val queue = new ConcurrentLinkedQueue[T]()

  override def offer(t: T): Unit =
    if (!queue.offer(t)) throw WebSocketBufferFull(queue.size)

  override def poll: Future[T] = {
    val p = Promise[T]()
    val tick = 10.millis

    def _poll(time: FiniteDuration = 0.millis): Unit = {
      if (time >= timeout) p.failure(new WebSocketTimeoutException)
      else
        window.setTimeout(
          () => {
            val t = queue.poll()
            if (t != null) p.success(t)
            else _poll(time + tick)
          },
          tick.toMillis.toDouble
        )
    }

    _poll()
    p.future
  }
}
