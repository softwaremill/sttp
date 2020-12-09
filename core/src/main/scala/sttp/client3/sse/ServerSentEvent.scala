package sttp.client3.sse

import scala.util.Try

case class ServerSentEvent(
    data: Option[String] = None,
    eventType: Option[String] = None,
    id: Option[String] = None,
    retry: Option[Int] = None
)

object ServerSentEvent {
  def parseEvent(event: List[String]): ServerSentEvent = {
    event.foldLeft(ServerSentEvent()) { (event, line) =>
      line.split(":").toList match {
        case "data" :: content      => combineData(event, content.mkString(":"))
        case "id" :: content        => event.copy(id = Some(content.mkString(":")))
        case "retry" :: content     => event.copy(retry = Try(content.mkString(":").toInt).toOption)
        case "event" :: content => event.copy(eventType = Some(content.mkString(":")))
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
