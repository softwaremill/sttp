// {cat=WebSocket; effects=Direct; backend=HttpClient}: Connect to & interact with a WebSocket, using Ox channels for streaming

//> using dep com.softwaremill.sttp.client4::ox:4.0.8

package sttp.client4.examples.ws

import ox.*
import ox.channels.Source
import sttp.client4.*
import sttp.client4.impl.ox.ws.*
import sttp.client4.ws.SyncWebSocket
import sttp.client4.ws.sync.*
import sttp.ws.WebSocketFrame

@main def wsOxExample =
  def useWebSocket(ws: SyncWebSocket): Unit =
    supervised:
      val inputs = Source.fromValues(1, 2, 3).map(i => WebSocketFrame.text(s"Frame no $i"))
      val (wsSource, wsSink) = asSourceAndSink(ws)
      forkDiscard:
        inputs.pipeTo(wsSink, propagateDone = true)
      wsSource.foreach: frame =>
        println(s"RECEIVED: $frame")

  val backend = DefaultSyncBackend()
  try
    basicRequest
      .get(uri"wss://ws.postman-echo.com/raw")
      .response(asWebSocket(useWebSocket))
      .send(backend)
      .discard
  finally
    backend.close()
