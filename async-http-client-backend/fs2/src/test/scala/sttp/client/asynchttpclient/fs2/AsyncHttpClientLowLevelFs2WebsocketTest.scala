package sttp.client.asynchttpclient.fs2

import cats.effect.{ContextShift, IO}
import com.github.ghik.silencer.silent
import org.asynchttpclient.ws.{WebSocketListener, WebSocket => AHCWebSocket}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.LowLevelListenerWebSocketTest

import scala.concurrent.Future

class AsyncHttpClientLowLevelFs2WebsocketTest
    extends LowLevelListenerWebSocketTest[IO, AHCWebSocket, WebSocketHandler] {
  implicit val cs: ContextShift[IO] = IO.contextShift(implicitly)
  implicit val backend: SttpBackend[IO, Nothing, WebSocketHandler] = AsyncHttpClientFs2Backend[IO]().unsafeRunSync()
  implicit val convertToFuture: ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }

  override def createHandler(_onTextFrame: String => Unit): WebSocketHandler[AHCWebSocket] =
    WebSocketHandler.fromListener(new WebSocketListener {
      override def onOpen(websocket: AHCWebSocket): Unit = {}
      override def onClose(websocket: AHCWebSocket, code: Int, reason: String): Unit = {}
      override def onError(t: Throwable): Unit = {}
      @silent("discarded")
      override def onTextFrame(payload: String, finalFragment: Boolean, rsv: Int): Unit = {
        _onTextFrame(payload)
      }
    })

  @silent("discarded")
  override def sendText(ws: AHCWebSocket, t: String): Unit = ws.sendTextFrame(t).await()

  @silent("discarded")
  override def sendCloseFrame(ws: AHCWebSocket): Unit = ws.sendCloseFrame()
}
