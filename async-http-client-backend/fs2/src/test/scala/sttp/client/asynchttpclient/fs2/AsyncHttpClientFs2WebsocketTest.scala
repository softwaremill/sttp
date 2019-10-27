package sttp.client.asynchttpclient.fs2

import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

import cats.effect.{ContextShift, IO}
import com.github.ghik.silencer.silent
import org.asynchttpclient.ws.{WebSocketListener, WebSocket => AHCWebSocket}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{AsyncFlatSpec, Matchers}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.testing.{ConvertToFuture, TestHttpServer, ToFutureWrapper}

import scala.collection.JavaConverters._
import scala.concurrent.Future

class AsyncHttpClientFs2WebsocketTest
    extends AsyncFlatSpec
    with Matchers
    with TestHttpServer
    with ToFutureWrapper
    with Eventually
    with IntegrationPatience {
  implicit val cs: ContextShift[IO] = IO.contextShift(implicitly)
  implicit val backend: SttpBackend[IO, Nothing, WebSocketHandler] = AsyncHttpClientFs2Backend[IO]().unsafeRunSync()
  implicit val convertToFuture: ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }

  it should "send and receive two messages" in {
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(WebSocketHandler.fromListener(collectingListener(received)))
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

  it should "send and receive two messages (pipe version)" in {
    val received = new ConcurrentLinkedQueue[String]()
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocket(WebSocketHandler.fromListener(collectingListener(received)))
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
      .openWebsocket(WebSocketHandler.fromListener(collectingListener(received)))
      .map { _ =>
        eventually {
          received.asScala.toList shouldBe List("test10", "test20")
        }
      }
      .toFuture
  }

  it should "error if the endpoint is not a websocket" in {
    basicRequest
      .get(uri"$wsEndpoint/echo")
      .openWebsocket(WebSocketHandler.fromListener(new WebSocketListener {
        override def onOpen(websocket: AHCWebSocket): Unit = {}
        override def onClose(websocket: AHCWebSocket, code: Int, reason: String): Unit = {}
        override def onError(t: Throwable): Unit = {}
      }))
      .redeemWith(IO.pure, _ => IO.raiseError(new NoSuchElementException("Should be a failed")))
      .map { t =>
        t shouldBe a[IOException]
      }
      .toFuture()
  }

  def collectingListener(queue: ConcurrentLinkedQueue[String]): WebSocketListener = new WebSocketListener {
    override def onOpen(websocket: AHCWebSocket): Unit = {}
    override def onClose(websocket: AHCWebSocket, code: Int, reason: String): Unit = {}
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
