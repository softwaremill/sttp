package sttp.client.asynchttpclient

import java.nio.channels.ClosedChannelException

import org.scalatest.Assertion
import sttp.client.{SttpClientException, basicRequest}
import sttp.client.monad.syntax._
import sttp.client.testing.websocket.WebSocketTest
import sttp.model.Uri._

import scala.concurrent.duration._
import sttp.client.testing.HttpTest.wsEndpoint

abstract class AsyncHttpClientHighLevelWebsocketTest[F[_]] extends WebSocketTest[F, WebSocketHandler] {

  it should "error if the endpoint is not a websocket" in {
    monad
      .handleError {
        basicRequest
          .get(uri"$wsEndpoint/echo")
          .openWebsocketF(createHandler(None))
          .map(_ => fail(): Assertion)
      } {
        case e: Exception => (e shouldBe a[SttpClientException.ReadException]).unit
      }
      .toFuture()
  }

  it should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocketF(createHandler(Some(3)))
      .flatMap { response =>
        val ws = response.result
        send(ws, 4) >>
          eventually(10.millis, 500) {
            ws.isOpen.map(_ shouldBe false)
          }
      }
      .handleError {
        case _: ClosedChannelException => succeed.unit
      }
      .toFuture()
  }
}
