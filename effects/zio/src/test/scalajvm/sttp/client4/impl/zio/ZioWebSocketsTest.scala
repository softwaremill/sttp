package sttp.client4.impl.zio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.Headers
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}
import zio.stream.ZStream
import zio.{Ref, Task, ZIO}

class ZioWebSocketsTest extends AnyFlatSpec with Matchers with ZioTestBase {

  behavior of "ZioWebSockets.compilePipe"

  private val serverClose = WebSocketFrame.Close(1000, "normal")

  /** Records all sent frames together with their `isContinuation` flag. `receive()` returns the given frames in order,
    * then fails with [[WebSocketClosed]], as a real web socket would.
    */
  private class RecordingWebSocket(
      incoming: Ref[List[WebSocketFrame]],
      sent: Ref[List[(WebSocketFrame, Boolean)]]
  ) extends WebSocket[Task] {
    override def receive(): Task[WebSocketFrame] =
      incoming.modify {
        case head :: tail => (ZIO.succeed(head): Task[WebSocketFrame], tail)
        case Nil          => (ZIO.fail(WebSocketClosed(None)): Task[WebSocketFrame], Nil)
      }.flatten

    override def send(frame: WebSocketFrame, isContinuation: Boolean): Task[Unit] =
      sent.update(_ :+ (frame -> isContinuation))

    override def isOpen(): Task[Boolean] = ZIO.succeed(true)
    override lazy val upgradeHeaders: Headers = Headers(Nil)
    override implicit def monad: MonadError[Task] = new RIOMonadAsyncError[Any]
  }

  private def sentFrames(incoming: List[WebSocketFrame])(
      pipe: ZStream[Any, Throwable, WebSocketFrame.Data[_]] => ZStream[Any, Throwable, WebSocketFrame]
  ): List[(WebSocketFrame, Boolean)] =
    unsafeRunSyncOrThrow(for {
      incomingRef <- Ref.make(incoming)
      sentRef <- Ref.make(List.empty[(WebSocketFrame, Boolean)])
      ws = new RecordingWebSocket(incomingRef, sentRef)
      _ <- ZioWebSockets.compilePipe[Any](ws, pipe)
      sent <- sentRef.get
    } yield sent)

  it should "mark non-final fragments emitted by the pipe as continuations of the previous frame" in {
    val first = WebSocketFrame.Text("Hel", finalFragment = false, None)
    val middle = WebSocketFrame.Text("lo, ", finalFragment = false, None)
    val last = WebSocketFrame.Text("world!", finalFragment = true, None)
    val separate = WebSocketFrame.text("Bye!")

    val sent = sentFrames(List(serverClose))(in => in.drain ++ ZStream(first, middle, last, separate))

    sent shouldBe List(
      first -> false,
      middle -> true,
      last -> true,
      separate -> false,
      serverClose -> false
    )
  }

  it should "echo a server-initiated Close exactly once, without a duplicate close frame" in {
    val message = WebSocketFrame.text("hello")

    val sent = sentFrames(List(serverClose))(in => in.drain ++ ZStream(message))

    sent shouldBe List(message -> false, serverClose -> false)
  }

  it should "send a Close frame emitted by the pipe exactly once" in {
    val userClose = WebSocketFrame.Close(4000, "user-initiated")

    val sent = sentFrames(Nil)(in => in.drain ++ ZStream(userClose))

    sent shouldBe List(userClose -> false)
  }

  it should "not mark a Close frame following a non-final fragment as a continuation" in {
    val fragment = WebSocketFrame.Text("partial", finalFragment = false, None)
    val userClose = WebSocketFrame.Close(4000, "abort")

    val sent = sentFrames(Nil)(in => in.drain ++ ZStream(fragment, userClose))

    sent shouldBe List(fragment -> false, userClose -> false)
  }

  it should "fall back to a default Close frame if neither the server nor the pipe provided one" in {
    val message = WebSocketFrame.text("hello")

    val sent = sentFrames(Nil)(in => in.drain ++ ZStream(message))

    sent shouldBe List(message -> false, WebSocketFrame.close -> false)
  }
}
