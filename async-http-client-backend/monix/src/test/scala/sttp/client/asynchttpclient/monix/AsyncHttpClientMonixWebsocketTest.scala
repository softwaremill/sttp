package sttp.client.asynchttpclient.monix

import java.util.concurrent.ConcurrentLinkedQueue

import com.github.ghik.silencer.silent
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.asynchttpclient.ws.{WebSocket, WebSocketListener}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{AsyncFlatSpec, Matchers}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.impl.monix.convertMonixTaskToFuture
import sttp.client.testing.{ConvertToFuture, TestHttpServer, ToFutureWrapper}

import scala.collection.JavaConverters._

class AsyncHttpClientMonixWebsocketTest
    extends AsyncFlatSpec
    with Matchers
    with TestHttpServer
    with ToFutureWrapper
    with Eventually
    with IntegrationPatience {
  implicit val backend: SttpBackend[Task, Nothing, WebSocketHandler] = AsyncHttpClientMonixBackend().runSyncUnsafe()
  implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  it should "send and receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(WebSocketHandler[WebSocket](collectingListener(received)))
      .map { response =>
        response.result.sendTextFrame("test1").await()
        response.result.sendTextFrame("test2").await()
        eventually {
          received.asScala.toList shouldBe List("echo: test1", "echo: test2")
        }
        response.result.sendCloseFrame().await()
        succeed
      }
      .toFuture
  }

  it should "receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_close")
      .openWebsocket(WebSocketHandler[WebSocket](collectingListener(received)))
      .map { _ =>
        eventually {
          received.asScala.toList shouldBe List("test10", "test20")
        }
      }
      .toFuture
  }

  def collectingListener(queue: ConcurrentLinkedQueue[String]): WebSocketListener = new WebSocketListener {
    override def onOpen(websocket: WebSocket): Unit = {}
    override def onClose(websocket: WebSocket, code: Int, reason: String): Unit = {}
    override def onError(t: Throwable): Unit = {}
    @silent("discarded")
    override def onTextFrame(payload: String, finalFragment: Boolean, rsv: Int): Unit = {
      queue.add(payload)
    }
  }

  override protected def afterAll(): Unit = {
    backend.close().toFuture
    super.afterAll()
  }
}
