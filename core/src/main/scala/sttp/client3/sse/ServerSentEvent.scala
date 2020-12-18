package sttp.client3.sse

import scala.util.Try

case class ServerSentEvent(
    data: Option[String] = None,
    eventType: Option[String] = None,
    id: Option[String] = None,
    retry: Option[Int] = None
)

object ServerSentEvent {
  // https://html.spec.whatwg.org/multipage/server-sent-events.html
  def parse(event: List[String]): ServerSentEvent = {
    event.foldLeft(ServerSentEvent()) { (event, line) =>
      if (line.startsWith("data:")) combineData(event, removeLeadingSpace(line.substring(5)))
      else if (line.startsWith("id:")) event.copy(id = Some(removeLeadingSpace(line.substring(3))))
      else if (line.startsWith("retry:")) event.copy(retry = Try(removeLeadingSpace(line.substring(6)).toInt).toOption)
      else if (line.startsWith("event:")) event.copy(eventType = Some(removeLeadingSpace(line.substring(6))))
      else if (line == "data") combineData(event, "")
      else if (line == "id") event.copy(id = Some(""))
      else if (line == "event") event.copy(eventType = Some(""))
      else event
    }
  }

  private def combineData(event: ServerSentEvent, newData: String): ServerSentEvent = {
    event match {
      case e @ ServerSentEvent(Some(oldData), _, _, _) => e.copy(data = Some(s"$oldData\n$newData"))
      case e @ ServerSentEvent(None, _, _, _)          => e.copy(data = Some(newData))
    }
  }

  private def removeLeadingSpace(s: String): String = if (s.startsWith(" ")) s.substring(1) else s
}
