package sttp.client3.impl.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client3.sse.ServerSentEvent
import zio.stream.ZTransducer

object ZioServerSentEvents {
  def decodeSSE(): ZioStreams.Pipe[Byte, ServerSentEvent] = { stream =>
    stream
      .aggregate(ZTransducer.utf8Decode)
      .aggregate(ZTransducer.splitLines)
      .aggregate(ZTransducer.collectAllWhile[String](_.nonEmpty))
      .map(ServerSentEvent.parseEvent)
  }
}
