package sttp.client3

import sttp.client3.internal.JSTimeout
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.ws.WebSocketTimeoutException
import sttp.ws.WebSocketBufferFull

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.{FiniteDuration, _}

private[client3] class JSSimpleQueue[F[_], T](timeout: FiniteDuration = 1.second)(implicit c: ConvertFromFuture[F])
    extends SimpleQueue[F, T] {

  private val queue = new ConcurrentLinkedQueue[T]()

  override def offer(t: T): Unit =
    if (!queue.offer(t)) throw WebSocketBufferFull(queue.size)

  override def poll: F[T] =
    c.fromFuture {
      JSTimeout[T] {
        val t = queue.poll()
        Either.cond(t != null, t, new WebSocketTimeoutException)
      }(timeout)
    }
}
