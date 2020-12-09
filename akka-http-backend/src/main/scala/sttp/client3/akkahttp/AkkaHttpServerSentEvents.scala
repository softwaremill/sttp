package sttp.client3.akkahttp

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Framing}
import akka.util.ByteString
import sttp.client3.sse.ServerSentEvent

object AkkaHttpServerSentEvents {

  def decodeSSE: Flow[ByteString, ServerSentEvent, NotUsed] =
    //todo decide on max frame length
    Framing
      .delimiter(ByteString("\n\n"), maximumFrameLength = Int.MaxValue, allowTruncation = true)
      .map(_.utf8String)
      .map(_.split("\n").toList)
      .map(ServerSentEvent.parseEvent)
}
