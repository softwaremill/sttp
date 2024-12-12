package sttp.client4.impl.ox.sse

import ox.flow.Flow
import sttp.model.sse.ServerSentEvent

import java.io.InputStream

object OxServerSentEvents:
  def parse(is: InputStream): Flow[ServerSentEvent] =
    Flow
      .fromInputStream(is)
      .linesUtf8
      .mapStatefulConcat(() => List.empty[String])(
        (lines, str) => if str.isEmpty then (Nil, List(lines)) else (lines :+ str, Nil),
        onComplete = { lines =>
          if lines.nonEmpty then Some(lines)
          else None
        }
      )
      .filter(_.nonEmpty)
      .map(ServerSentEvent.parse)
