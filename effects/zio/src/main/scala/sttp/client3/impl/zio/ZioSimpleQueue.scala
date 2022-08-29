package sttp.client3.impl.zio

import sttp.client3.internal.ws.SimpleQueue
import sttp.ws.WebSocketBufferFull
import zio.{Queue, RIO, Runtime, Unsafe}

private[client3] class ZioSimpleQueue[R, A](queue: Queue[A], runtime: Runtime[Any]) extends SimpleQueue[RIO[R, *], A] {
  override def offer(t: A): Unit = {
    Unsafe.unsafeCompat { implicit u =>
      if (!runtime.unsafe.run(queue.offer(t)).getOrThrowFiberFailure()) {
        throw WebSocketBufferFull(queue.capacity)
      }
    }
  }
  override def poll: RIO[R, A] = {
    queue.take
  }
}
