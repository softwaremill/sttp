package sttp.client3.impl.monix

import monix.reactive.Pipe
import sttp.client3.sse.ServerSentEvent

object MonixServerSentEvents {
  def decodeSSE: Pipe[Array[Byte], ServerSentEvent] = ???
}
