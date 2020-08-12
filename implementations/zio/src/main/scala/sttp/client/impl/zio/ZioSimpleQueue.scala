package sttp.client.impl.zio

import sttp.client.ws.internal.SimpleQueue
import sttp.model.ws.WebSocketBufferFull
import zio.{Queue, RIO, Runtime}

class ZioSimpleQueue[R, A](queue: Queue[A], runtime: Runtime[Any]) extends SimpleQueue[RIO[R, *], A] {
  override def offer(t: A): Unit = {
    if (!runtime.unsafeRun(queue.offer(t))) {
      throw new WebSocketBufferFull()
    }
  }
  override def poll: RIO[R, A] = {
    queue.take
  }
}
