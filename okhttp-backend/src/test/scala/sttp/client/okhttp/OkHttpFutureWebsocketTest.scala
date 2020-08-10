package sttp.client.okhttp

import org.scalatest.Assertion
import sttp.client._
import sttp.client.monad.{FutureMonad, MonadError}
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.testing.websocket.WebSocketTest

import scala.concurrent.{Future, blocking}
import scala.concurrent.duration._

class OkHttpFutureWebsocketTest extends WebSocketTest[Future] {
  override implicit val backend: SttpBackend[Future, WebSockets] = OkHttpFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monad: MonadError[Future] = new FutureMonad()

  override def throwsWhenNotAWebSocket: Boolean = true

  it should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways[Future, Assertion] { ws =>
        for {
          _ <- send(ws, OkHttpBackend.DefaultWebSocketBufferCapacity.get + 1)
          result <- eventually(10.millis, 400)(() => ws.isOpen.map(_ shouldBe false))
        } yield result
      })
      .send()
      .map(_.body)
      .recover {
        case _: SendMessageException => succeed
      }
  }

  private def eventually[T](interval: FiniteDuration, attempts: Int)(f: () => Future[T]): Future[T] = {
    Future(blocking(Thread.sleep(interval.toMillis))).flatMap(_ => f()).recoverWith {
      case _ if attempts > 0 => eventually(interval, attempts - 1)(f)
    }
  }
}
