package sttp.client3.httpclient.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client3.sse.ServerSentEvent
import zio.stream
import zio.stream.ZTransducer

import scala.util.Try

object ZioServerSentEvents {
  def decodeSSE(): ZioStreams.Pipe[Byte, ServerSentEvent] = { stream: stream.Stream[Throwable, Byte] =>
    stream
      .aggregate(ZTransducer.utf8Decode)
      .aggregate(ZTransducer.splitLines)
      .aggregate(ZTransducer.collectAllWhile[String](_.nonEmpty))
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
