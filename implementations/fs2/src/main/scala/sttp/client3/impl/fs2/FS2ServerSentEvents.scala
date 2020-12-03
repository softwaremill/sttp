package sttp.client3.impl.fs2

import fs2.text
import sttp.client3.sse.ServerSentEvent

object FS2ServerSentEvents {
  def decodeSSE[F[_]](response: fs2.Stream[F, Byte]): fs2.Stream[F, ServerSentEvent] = {
    response
      .through(text.utf8Decode[F])
      .through(text.lines[F])
      .split(_.isEmpty)
      .map(_.toList)
      .map { event =>
        //todo parse the event
        ServerSentEvent()
      }
  }
}
