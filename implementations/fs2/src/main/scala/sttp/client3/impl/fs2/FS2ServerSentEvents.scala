package sttp.client3.impl.fs2

import fs2.text
import sttp.client3.sse.ServerSentEvent

import scala.util.Try

object FS2ServerSentEvents {
  def decodeSSE[F[_]](response: fs2.Stream[F, Byte]): fs2.Stream[F, ServerSentEvent] = {
    response
      .through(text.utf8Decode[F])
      .through(text.lines[F])
      .split(_.isEmpty)
      .map(_.toList)
      .map { event =>
        event.foldLeft(ServerSentEvent()) { (event, line) =>
          line.span(_ == ':') match {
            case ("data", content)      => combineData(event, content)
            case ("id", content)        => event.copy(id = Some(content))
            case ("retry", content)     => event.copy(retry = Try(content.toInt).toOption)
            case ("eventType", content) => event.copy(eventType = Some(content))
            case _                      => event
          }
        }
      }
  }

  private def combineData(event: ServerSentEvent, newData: String): ServerSentEvent = {
    event match {
      case e @ ServerSentEvent(Some(oldData), _, _, _) => e.copy(data = Some(s"$oldData\n$newData"))
      case e @ ServerSentEvent(None, _, _, _)          => e.copy(data = Some(newData))
    }
  }
}
