package sttp.client.okhttp

import org.scalatest.Assertion
import sttp.client._
import sttp.client.internal.NoStreams
import sttp.client.monad.syntax._
import sttp.client.monad.{FutureMonad, IdMonad, MonadError}
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.testing.websocket.WebSocketTest
import sttp.client.ws.WebSocket

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Future, blocking}

class OkHttpSyncWebsocketTest extends WebSocketTest[Identity, Nothing] {
  override val streams: NoStreams = NoStreams
  override implicit val backend: SttpBackend[Identity, Nothing with WebSockets] =
    OkHttpSyncBackend().asInstanceOf[SttpBackend[Identity, Nothing with WebSockets]] //TODO how to remove nothing
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
  override implicit val monad: MonadError[Identity] = IdMonad

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

  ignore should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocket[Identity, Assertion] { ws =>
        send(ws, OkHttpBackend.DefaultWebSocketBufferCapacity.get + 1) >> eventually(10 millis, 400)(() =>
          ws.isOpen.map(_ shouldBe false)
        )
      })
      .send()
      .map(_.body) match {
      case Left(value)  => throw new RuntimeException(value)
      case Right(value) => succeed
    }
  }

  private def eventually[T](interval: FiniteDuration, attempts: Int)(f: () => T): T = {
    Thread.sleep(interval.toMillis)
    try f()
    catch {
      case e if attempts > 0 => eventually(interval, attempts - 1)(f)
    }
  }
}
