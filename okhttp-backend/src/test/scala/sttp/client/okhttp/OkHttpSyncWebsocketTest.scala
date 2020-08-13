package sttp.client.okhttp

import org.scalatest.Assertion
import sttp.capabilities.WebSockets
import sttp.client._
import sttp.monad.syntax._
import sttp.client.monad.IdMonad
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.testing.websocket.WebSocketTest
import sttp.monad.MonadError

import scala.annotation.tailrec
import scala.concurrent.duration._

class OkHttpSyncWebsocketTest extends WebSocketTest[Identity] {
  override val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
  override implicit val monad: MonadError[Identity] = IdMonad

  override def throwsWhenNotAWebSocket: Boolean = true

  it should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocket[Identity, Assertion] { ws =>
        send(ws, OkHttpBackend.DefaultWebSocketBufferCapacity.get + 1).flatMap(_ =>
          eventually(10.millis, 400)(() => ws.isOpen().map(_ shouldBe false))
        )
      })
      .send(backend)
      .map(_.body) match {
      case Left(value) => throw new RuntimeException(value)
      case Right(_)    => succeed
    }
  }

  @tailrec
  private def eventually[T](interval: FiniteDuration, attempts: Int)(f: () => T): T = {
    Thread.sleep(interval.toMillis)
    try f()
    catch {
      case e if attempts > 0 => eventually(interval, attempts - 1)(f)
    }
  }
}
