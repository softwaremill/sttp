package sttp.client4.impl.fs2

import fs2.text
import sttp.model.sse.ServerSentEvent
import fs2.text.utf8

object Fs2ServerSentEvents {
  def parse[F[_]]: fs2.Pipe[F, Byte, ServerSentEvent] = { response =>
    response
      .through(utf8.decode[F])
      .through(text.lines[F])
      .split(_.isEmpty)
      .filter(_.nonEmpty)
      .map(_.toList)
      .map(ServerSentEvent.parse)
  }
}
