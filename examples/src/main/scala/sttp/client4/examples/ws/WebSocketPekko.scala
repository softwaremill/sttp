// {cat=WebSocket; effects=Future; backend=Pekko}: Connect to & interact with a WebSocket

//> using dep com.softwaremill.sttp.client4::pekko-http-backend:4.0.18
//> using dep org.apache.pekko::pekko-stream:1.1.2

package sttp.client4.examples.ws

import sttp.client4._
import sttp.client4.ws.async._
import sttp.client4.pekkohttp.PekkoHttpBackend
import sttp.ws.WebSocket

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@main def webSocketPekko(): Unit =
  def useWebSocket(ws: WebSocket[Future]): Future[Unit] =
    def send(i: Int) = ws.sendText(s"Hello $i!")
    def receive() = ws.receiveText().map(t => println(s"RECEIVED: $t"))
    for
      _ <- send(1)
      _ <- send(2)
      _ <- receive()
      _ <- receive()
    yield ()

  val backend = PekkoHttpBackend()

  basicRequest
    .get(uri"wss://ws.postman-echo.com/raw")
    .response(asWebSocket(useWebSocket))
    .send(backend)
    .onComplete(_ => backend.close())
