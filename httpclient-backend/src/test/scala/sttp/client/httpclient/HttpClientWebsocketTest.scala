package sttp.client.httpclient

import java.net.http.WebSocket
import java.net.http.WebSocket.Listener
import java.util.concurrent.{CompletionStage, ConcurrentLinkedQueue}

import com.github.ghik.silencer.silent
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{AsyncFlatSpec, Matchers}
import sttp.client._
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.testing.{ConvertToFuture, TestHttpServer, ToFutureWrapper}

import scala.collection.JavaConverters._

abstract class HttpClientWebsocketTest[F[_]]
    extends AsyncFlatSpec
    with Matchers
    with TestHttpServer
    with ToFutureWrapper
    with Eventually
    with IntegrationPatience {
  implicit val backend: SttpBackend[F, _, WebSocketHandler]
  implicit val convertToFuture: ConvertToFuture[F]
  implicit val monadError: MonadError[F]

  it should "send and receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(WebSocketHandler.fromListener(collectingListener(received)))
      .map { response =>
        response.result.sendText("test1", true).get()
        response.result.sendText("test2", true).get()
        eventually {
          received.asScala.toList shouldBe List("echo: ", "test1", "", "echo: ", "test2", "")
        }
        val ws = response.result.sendClose(1000, "").get()
        ws.isOutputClosed shouldBe true
      }
      .toFuture
  }

  it should "receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_close")
      .openWebsocket(WebSocketHandler.fromListener(collectingListener(received)))
      .map { _ =>
        eventually {
          received.asScala.toList shouldBe List("test10", "test20")
        }
      }
      .toFuture()
  }

  def collectingListener(queue: ConcurrentLinkedQueue[String]): Listener = new Listener {
    @silent("discarded")
    override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[_] = {
      queue.add(data.toString)
      super.onText(webSocket, data, last)
    }
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }
}
