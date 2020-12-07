package sttp.client3.sse

import scala.util.Try

case class ServerSentEvent(
    data: Option[String] = None,
    eventType: Option[String] = None,
    id: Option[String] = None,
    retry: Option[Int] = None
)

object ServerSentEvent{
  def parseEvent(event:List[String]):ServerSentEvent={
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

  private def combineData(event: ServerSentEvent, newData: String): ServerSentEvent = {
    event match {
      case e @ ServerSentEvent(Some(oldData), _, _, _) => e.copy(data = Some(s"$oldData\n$newData"))
      case e @ ServerSentEvent(None, _, _, _)          => e.copy(data = Some(newData))
    }
  }
}