package sttp.client.asynchttpclient

import java.io.IOException
import java.nio.channels.ClosedChannelException

import org.scalatest.Assertion
import sttp.client.basicRequest
import sttp.client.testing.websocket.WebsocketHandlerTest
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame
import sttp.client.monad.syntax._
import sttp.model.Uri._

abstract class AHCWebsocketHandlerTest[F[_]] extends WebsocketHandlerTest[F, WebSocketHandler] {

  it should "error if the endpoint is not a websocket" in {
    monad
      .handleError {
        basicRequest
          .get(uri"$wsEndpoint/echo")
          .openWebsocket(createHandler(None))
          .map(_ => fail: Assertion)
      } {
        case e: Exception => (e shouldBe a[IOException]).unit
      }
      .toFuture()
  }

  it should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(createHandler(Some(3)))
      .flatMap { response =>
        val ws = response.result
        send(ws, 4) >>
          eventually {
            ws.isOpen.map(_ shouldBe false)
          }
      }
      .handleError {
        case _: ClosedChannelException => succeed.unit
      }
      .toFuture()
  }

  def receiveEcho(ws: WebSocket[F], count: Int): F[Assertion] = {
    val fs = (1 to count).map(i => ws.receive.map(_ shouldBe Right(WebSocketFrame.text(s"echo: test$i"))))
    fs.foldLeft(succeed.unit)(_ >> _)
  }
}
