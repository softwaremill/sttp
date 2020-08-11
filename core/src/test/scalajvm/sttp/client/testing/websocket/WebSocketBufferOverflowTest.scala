package sttp.client.testing.websocket

import java.nio.channels.ClosedChannelException

import org.scalatest.Suite
import org.scalatest.flatspec.AsyncFlatSpecLike
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.ws.WebSocket
import sttp.client._
import sttp.client.monad.MonadError
import sttp.client.testing.ConvertToFuture
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import sttp.client.monad.syntax._

trait WebSocketBufferOverflowTest[F[_]] { outer: Suite with AsyncFlatSpecLike with WebSocketTest[F] =>
  implicit val backend: SttpBackend[F, WebSockets]
  implicit val monad: MonadError[F]
  implicit val convertToFuture: ConvertToFuture[F]
  def bufferCapacity: Int

  it should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { ws: WebSocket[F] =>
        send(ws, bufferCapacity + 1).flatMap { _ =>
          eventually(10.millis, 500) {
            ws.isOpen.map(_ shouldBe false)
          }
        }
      })
      .send()
      .map(_.body)
      .handleError {
        case _: ClosedChannelException => succeed.unit
      }
      .toFuture()
  }

  def eventually[T](interval: FiniteDuration, attempts: Int)(f: => F[T]): F[T]
}
