package sttp.client4.impl.fs2

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.unsafe.implicits.global
import fs2.{Pipe, Stream}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.model.Headers
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}

class Fs2WebSocketsTest extends AnyFlatSpec with Matchers {

  behavior of "Fs2WebSockets.handleThroughPipe"

  private val serverClose = WebSocketFrame.Close(1000, "normal")

  /** Records all sent frames together with their `isContinuation` flag. `receive()` returns the given frames in order,
    * then fails with [[WebSocketClosed]], as a real web socket would.
    */
  private class RecordingWebSocket(
      incoming: Ref[IO, List[WebSocketFrame]],
      sent: Ref[IO, List[(WebSocketFrame, Boolean)]]
  ) extends WebSocket[IO] {
    override def receive(): IO[WebSocketFrame] =
      incoming
        .modify {
          case head :: tail => (tail, IO.pure(head))
          case Nil          => (Nil, IO.raiseError[WebSocketFrame](WebSocketClosed(None)))
        }
        .flatMap(identity)

    override def send(frame: WebSocketFrame, isContinuation: Boolean): IO[Unit] =
      sent.update(_ :+ (frame -> isContinuation))

    override def isOpen(): IO[Boolean] = IO.pure(true)
    override lazy val upgradeHeaders: Headers = Headers(Nil)
    override implicit def monad: MonadError[IO] = new CatsMonadAsyncError[IO]
  }

  private def sentFrames(incoming: List[WebSocketFrame])(
      pipe: Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame]
  ): List[(WebSocketFrame, Boolean)] =
    (for {
      incomingRef <- Ref.of[IO, List[WebSocketFrame]](incoming)
      sentRef <- Ref.of[IO, List[(WebSocketFrame, Boolean)]](Nil)
      ws = new RecordingWebSocket(incomingRef, sentRef)
      _ <- Fs2WebSockets.handleThroughPipe(ws)(pipe)
      sent <- sentRef.get
    } yield sent).unsafeRunSync()

  it should "mark non-final fragments emitted by the pipe as continuations of the previous frame" in {
    val first = WebSocketFrame.Text("Hel", finalFragment = false, None)
    val middle = WebSocketFrame.Text("lo, ", finalFragment = false, None)
    val last = WebSocketFrame.Text("world!", finalFragment = true, None)
    val separate = WebSocketFrame.text("Bye!")

    val sent = sentFrames(List(serverClose))(in => in.drain ++ Stream(first, middle, last, separate))

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

    val sent = sentFrames(List(serverClose))(in => in.drain ++ Stream(message))

    sent shouldBe List(message -> false, serverClose -> false)
  }

  it should "send a Close frame emitted by the pipe exactly once" in {
    val userClose = WebSocketFrame.Close(4000, "user-initiated")

    val sent = sentFrames(Nil)(in => in.drain ++ Stream(userClose))

    sent shouldBe List(userClose -> false)
  }

  it should "not mark a Close frame following a non-final fragment as a continuation" in {
    val fragment = WebSocketFrame.Text("partial", finalFragment = false, None)
    val userClose = WebSocketFrame.Close(4000, "abort")

    val sent = sentFrames(Nil)(in => in.drain ++ Stream(fragment, userClose))

    sent shouldBe List(fragment -> false, userClose -> false)
  }

  it should "fall back to a default Close frame if neither the server nor the pipe provided one" in {
    val message = WebSocketFrame.text("hello")

    val sent = sentFrames(Nil)(in => in.drain ++ Stream(message))

    sent shouldBe List(message -> false, WebSocketFrame.close -> false)
  }
}
