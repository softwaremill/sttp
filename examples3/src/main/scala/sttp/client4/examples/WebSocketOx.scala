package sttp.client4.examples

import sttp.client4.*
import sttp.client4.ws.SyncWebSocket
import sttp.client4.ws.sync.*
import _root_.ox.*
import sttp.client4.ox.ws.*
import _root_.ox.channels.Source
import sttp.ws.WebSocketFrame

object WebSocketOx extends App:
  def useWebSocket(ws: SyncWebSocket): Unit =
    supervised {
      val inputs = Source.fromValues(1, 2, 3).map(i => WebSocketFrame.text(s"Frame no $i"))
      val (wsSource, wsSink) = ws.asSourceAndSink
      fork {
        inputs.pipeTo(wsSink)
      }
      wsSource.foreach { frame =>
        println(s"RECEIVED: $frame")
      }
    }

  val backend = DefaultSyncBackend()
  try
    basicRequest
      .get(uri"wss://ws.postman-echo.com/raw")
      .response(asWebSocket(useWebSocket))
      .send(backend)
  finally
    backend.close()
