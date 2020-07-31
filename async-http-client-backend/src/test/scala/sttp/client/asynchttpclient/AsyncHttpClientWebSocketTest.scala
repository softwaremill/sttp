package sttp.client.asynchttpclient

import java.nio.channels.ClosedChannelException

import sttp.client.{asWebSocketAlways, basicRequest}
import sttp.client.monad.syntax._
import sttp.client.testing.websocket.WebSocketTest
import sttp.model.Uri._
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.ws.WebSocket

import scala.concurrent.duration._

abstract class AsyncHttpClientWebSocketTest[F[_], S] extends WebSocketTest[F, S] {

  it should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { ws: WebSocket[F] =>
        send(ws, AsyncHttpClientBackend.DefaultWebSocketBufferCapacity.get + 1) >>
          eventually(10.millis, 500) {
            ws.isOpen.map(_ shouldBe false)
          }
      })
      .send()
      .map(_.body)
      .handleError {
        case _: ClosedChannelException => succeed.unit
      }
      .toFuture()
  }

  override def throwsWhenNotAWebSocket: Boolean = true

  def eventually[T](interval: FiniteDuration, attempts: Int)(f: => F[T]): F[T]
}
