package sttp.client.asynchttpclient

import java.io.IOException
import java.nio.channels.ClosedChannelException

import org.scalatest.Assertion
import sttp.client.basicRequest
import sttp.client.monad.syntax._
import sttp.client.testing.websocket.HighLevelWebsocketTest
import sttp.model.Uri._

import scala.concurrent.duration._

abstract class AsyncHttpClientHighLevelWebsocketTest[F[_]] extends HighLevelWebsocketTest[F, WebSocketHandler] {

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
          eventually(10.millis, 100) {
            ws.isOpen.map(_ shouldBe false)
          }
      }
      .handleError {
        case _: ClosedChannelException => succeed.unit
      }
      .toFuture()
  }
}
