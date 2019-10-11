package sttp.client.asynchttpclient.monix

import java.io.IOException

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{AsyncFlatSpec, Matchers}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.impl.monix.convertMonixTaskToFuture
import sttp.client.testing.{ConvertToFuture, TestHttpServer, ToFutureWrapper}
import cats.implicits._
import sttp.model.ws.WebSocketFrame

class MonixWebsocketHandlerTest
    extends AsyncFlatSpec
    with Matchers
    with TestHttpServer
    with ToFutureWrapper
    with Eventually
    with IntegrationPatience {
  implicit val backend: SttpBackend[Task, Nothing, WebSocketHandler] = AsyncHttpClientMonixBackend().runSyncUnsafe()
  implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  it should "send and receive two messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(MonixWebSocketHandler.create)
      .flatMap { response =>
        val mws = response.result
        mws.send(WebSocketFrame.text("test1")) >>
          mws.send(WebSocketFrame.text("test2")) >>
          mws.receive.map(_ shouldBe WebSocketFrame.text("echo: test1").asRight) >>
          mws.receive.map(_ shouldBe WebSocketFrame.text("echo: test2").asRight) >>
          mws.close >>
          Task(succeed)
      }
      .toFuture
  }

  it should "receive two messages" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_close")
      .openWebsocket(MonixWebSocketHandler.create)
      .flatMap { response =>
        val mws = response.result
        mws.receive.map(_ shouldBe WebSocketFrame.text("test10").asRight) >>
          mws.receive.map(_ shouldBe WebSocketFrame.text("test20").asRight) >>
          mws.receive.map(_ shouldBe 'left)
      }
      .toFuture
  }

  it should "error if the endpoint is not a websocket" in {
    basicRequest
      .get(uri"$wsEndpoint/echo")
      .openWebsocket(MonixWebSocketHandler.create)
      .failed
      .map { t =>
        t shouldBe a[IOException]
      }
      .toFuture()
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture
    super.afterAll()
  }
}
