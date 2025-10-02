// {cat=WebSocket; effects=Direct; backend=HttpClient}: Connect to & interact with a WebSocket

//> using dep com.softwaremill.sttp.client4::core:4.0.12

package sttp.client4.examples.ws

import sttp.client4.*
import sttp.client4.ws.SyncWebSocket
import sttp.client4.ws.sync.*

@main def webSocketSynchronous(): Unit =
  def useWebSocket(ws: SyncWebSocket): Unit =
    def send(i: Int): Unit = ws.sendText(s"Hello $i!")
    def receive(): Unit = {
      val t = ws.receiveText()
      println(s"RECEIVED: $t")
    }
    send(1)
    send(2)
    receive()
    receive()

  val backend = DefaultSyncBackend()

  try
    println(
      basicRequest
        .get(uri"wss://ws.postman-echo.com/raw")
        .response(asWebSocket(useWebSocket))
        .send(backend)
    )
  finally backend.close()
