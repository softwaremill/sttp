package sttp.client.okhttp

import java.net.ProtocolException
import java.util.concurrent.ConcurrentLinkedQueue

import com.github.ghik.silencer.silent
import okhttp3.{WebSocket, WebSocketListener}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{FlatSpec, Matchers}
import sttp.client._
import sttp.client.testing.{TestHttpServer, ToFutureWrapper}

import scala.collection.JavaConverters._

class OkHttpSyncWebsocketTest
    extends FlatSpec
    with Matchers
    with TestHttpServer
    with ToFutureWrapper
    with Eventually
    with IntegrationPatience {
  implicit val backend: SttpBackend[Identity, Nothing, WebSocketHandler] = OkHttpSyncBackend()

  it should "send and receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    val response = basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(WebSocketHandler.fromListener(collectingListener(received)))

    response.result.send("test1") shouldBe true
    response.result.send("test2") shouldBe true
    eventually {
      received.asScala.toList shouldBe List("echo: test1", "echo: test2")
    }
    response.result.close(1000, null) shouldBe true
  }

  it should "receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_close")
      .openWebsocket(WebSocketHandler.fromListener(collectingListener(received)))

    eventually {
      received.asScala.toList shouldBe List("test10", "test20")
    }
  }

  it should "error if the endpoint is not a websocket" in {
    val t = intercept[Throwable] {
      basicRequest
        .get(uri"$wsEndpoint/echo")
        .openWebsocket(WebSocketHandler.fromListener(new WebSocketListener {}))
    }

    t shouldBe a[ProtocolException]
  }

  def collectingListener(queue: ConcurrentLinkedQueue[String]): WebSocketListener = new WebSocketListener {
    @silent("discarded")
    override def onMessage(webSocket: WebSocket, text: String): Unit = {
      queue.add(text)
    }
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
