package sttp.client.okhttp

import org.scalatest.Assertion
import sttp.client._
import sttp.client.internal.NoStreams
import sttp.client.monad.{FutureMonad, MonadError}
import sttp.client.monad.syntax._
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.testing.websocket.WebSocketTest
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame

import scala.concurrent.{Future, blocking}
import scala.concurrent.duration._

class OkHttpFutureWebsocketTest extends WebSocketTest[Future, Nothing] {
  override val streams: NoStreams = NoStreams
  override implicit val backend: SttpBackend[Future, Nothing with WebSockets] =
    OkHttpFutureBackend().asInstanceOf[SttpBackend[Future, Nothing with WebSockets]] //TODO how to remove nothing
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monad: MonadError[Future] = new FutureMonad()

  override def functionToPipe(f: sttp.model.ws.WebSocketFrame.Data[_] => sttp.model.ws.WebSocketFrame): Nothing =
    throw new IllegalStateException()

  ignore should "error if the endpoint is not a websocket" in {
    monad
      .handleError {
        basicRequest
          .get(uri"$wsEndpoint/echo")
          .response(asWebSocket { _: WebSocket[Future] => Future(()) })
          .send()
          .map(_ => fail: Assertion)
      } {
        case e: Exception => (e shouldBe a[SttpClientException.ReadException]).unit
      }
      .toFuture()
  }

  it should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways[Future, Assertion] { ws =>
        send(ws, OkHttpBackend.DefaultWebSocketBufferCapacity.get + 1) >> eventually(10.millis, 400)(() =>
          ws.isOpen.map(_ shouldBe false)
        )
      })
      .send()
      .map(_.body)
      .recover {
        case _: SendMessageException => succeed
      }
  }

  private def eventually[T](interval: FiniteDuration, attempts: Int)(f: () => Future[T]): Future[T] = {
    println("eventually")
    (Future(blocking(Thread.sleep(interval.toMillis))) >> f()).recoverWith {
      case e if attempts > 0 => eventually(interval, attempts - 1)(f)
    }
  }
}
