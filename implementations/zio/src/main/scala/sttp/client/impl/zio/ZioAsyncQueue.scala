package sttp.client.impl.zio

import sttp.client.ws.internal.AsyncQueue
import sttp.model.ws.WebSocketBufferFull
import zio.{Queue, RIO, Runtime}

class ZioAsyncQueue[R, A](queue: Queue[A], runtime: Runtime[Any]) extends AsyncQueue[RIO[R, *], A] {
  override def offer(t: A): Unit = {
    if (!runtime.unsafeRun(queue.offer(t))) {
      throw new WebSocketBufferFull()
    }
  }
  override def poll: RIO[R, A] = {
    queue.take
  }
}
