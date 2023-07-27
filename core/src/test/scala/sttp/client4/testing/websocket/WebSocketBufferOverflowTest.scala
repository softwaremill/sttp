package sttp.client4.testing.websocket

import java.nio.channels.ClosedChannelException

import org.scalatest.Suite
import org.scalatest.flatspec.AsyncFlatSpecLike
import sttp.client4.testing.HttpTest.wsEndpoint
import sttp.client4._
import sttp.client4.ws.async._
import sttp.monad.MonadError
import sttp.client4.testing.ConvertToFuture

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import sttp.monad.syntax._
import sttp.ws.WebSocket

trait WebSocketBufferOverflowTest[F[_]] { outer: Suite with AsyncFlatSpecLike with WebSocketTest[F] =>
  implicit def monad: MonadError[F]
  implicit val convertToFuture: ConvertToFuture[F]
  def bufferCapacity: Int

  it should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { (ws: WebSocket[F]) =>
        sendText(ws, bufferCapacity + 1).flatMap { _ =>
          eventually(100.millis, 50) {
            ws.isOpen().map(_ shouldBe false)
          }
        }
      })
      .send(backend)
      .map(_.body)
      .handleError {
        case e if e.getCause.isInstanceOf[ClosedChannelException] => succeed.unit
      }
      .toFuture()
  }

  def eventually[T](interval: FiniteDuration, attempts: Int)(f: => F[T]): F[T]
}
