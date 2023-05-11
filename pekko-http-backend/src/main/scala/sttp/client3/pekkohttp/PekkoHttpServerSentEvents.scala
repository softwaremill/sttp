package sttp.client3.pekkohttp

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Flow, Framing}
import org.apache.pekko.util.ByteString
import sttp.model.sse.ServerSentEvent

object PekkoHttpServerSentEvents {
  val parse: Flow[ByteString, ServerSentEvent, NotUsed] =
    Framing
      .delimiter(ByteString("\n\n"), maximumFrameLength = Int.MaxValue, allowTruncation = true)
      .map(_.utf8String)
      .map(_.split("\n").toList)
      .map(ServerSentEvent.parse)
}
