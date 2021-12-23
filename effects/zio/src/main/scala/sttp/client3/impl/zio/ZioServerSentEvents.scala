package sttp.client3.impl.zio

import sttp.capabilities.zio.ZioStreams
import sttp.model.sse.ServerSentEvent
import zio.stream.ZPipeline

object ZioServerSentEvents {
  val parse: ZioStreams.Pipe[Byte, ServerSentEvent] = { stream =>
    stream
      .via(ZPipeline.utf8Decode)
      .via(ZPipeline.splitLines)
      .split(_.isEmpty)
      .map(c => ServerSentEvent.parse(c.toList))
  }
}
