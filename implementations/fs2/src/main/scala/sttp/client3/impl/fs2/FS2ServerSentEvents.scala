package sttp.client3.impl.fs2

import fs2.text
import sttp.client3.sse.ServerSentEvent

object FS2ServerSentEvents {
  def decodeSSE[F[_]]: fs2.Pipe[F, Byte, ServerSentEvent] = { response: fs2.Stream[F, Byte] =>
    response
      .through(text.utf8Decode[F])
      .through(text.lines[F])
      .split(_.isEmpty)
      .map(_.toList)
      .map(ServerSentEvent.parseEvent)
  }
}
