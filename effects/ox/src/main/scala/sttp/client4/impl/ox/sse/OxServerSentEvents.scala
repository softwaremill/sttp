package sttp.client4.impl.ox.sse

import ox.*
import ox.channels.Source
import sttp.model.sse.ServerSentEvent

import java.io.InputStream

object OxServerSentEvents:
  def parse(is: InputStream)(using Ox, IO): Source[ServerSentEvent] =
    Source
      .fromInputStream(is)
      .linesUtf8
      .mapStatefulConcat(() => List.empty[String])(
        (lines, str) => if str.isEmpty then (Nil, List(lines)) else (lines :+ str, Nil),
        onComplete = { lines =>
          if lines.nonEmpty then Some(lines)
          else None
        }
      )
      .filterAsView(_.nonEmpty)
      .map(ServerSentEvent.parse)
