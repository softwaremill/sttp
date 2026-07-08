package sttp.client4.impl.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.Headers
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}

import scala.concurrent.duration._

class MonixWebSocketsTest extends AnyFlatSpec with Matchers {

  behavior of "MonixWebSockets.compilePipe"

  private val serverClose = WebSocketFrame.Close(1000, "normal")

  /** Records all sent frames together with their `isContinuation` flag. `receive()` returns the given frames in order,
    * then fails with [[WebSocketClosed]], as a real web socket would.
    */
  private class RecordingWebSocket(incoming: List[WebSocketFrame]) extends WebSocket[Task] {
    private var remaining = incoming
    private val recorded = scala.collection.mutable.ListBuffer.empty[(WebSocketFrame, Boolean)]
    def sent: List[(WebSocketFrame, Boolean)] = synchronized(recorded.toList)

    override def receive(): Task[WebSocketFrame] = Task.defer {
      synchronized {
        remaining match {
          case head :: tail => remaining = tail; Task.now(head)
          case Nil          => Task.raiseError(WebSocketClosed(None))
        }
      }
    }

    override def send(frame: WebSocketFrame, isContinuation: Boolean): Task[Unit] =
      Task { synchronized { recorded += (frame -> isContinuation) }; () }

    override def isOpen(): Task[Boolean] = Task.now(true)
    override lazy val upgradeHeaders: Headers = Headers(Nil)
    override implicit def monad: MonadError[Task] = TaskMonadAsyncError
  }

  private def sentFrames(incoming: List[WebSocketFrame])(
      pipe: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
  ): List[(WebSocketFrame, Boolean)] = {
    val ws = new RecordingWebSocket(incoming)
    MonixWebSockets.compilePipe(ws, pipe).runSyncUnsafe(5.seconds)
    ws.sent
  }

  // drains the incoming frames (as compilePipe requires), then emits the given frames
  private def drainThen(frames: WebSocketFrame*): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] =
    in => Observable.fromTask(in.completedL).flatMap(_ => Observable(frames: _*))

  it should "mark non-final fragments emitted by the pipe as continuations of the previous frame" in {
    val first = WebSocketFrame.Text("Hel", finalFragment = false, None)
    val middle = WebSocketFrame.Text("lo, ", finalFragment = false, None)
    val last = WebSocketFrame.Text("world!", finalFragment = true, None)
    val separate = WebSocketFrame.text("Bye!")

    val sent = sentFrames(List(serverClose))(drainThen(first, middle, last, separate))

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

    val sent = sentFrames(List(serverClose))(drainThen(message))

    sent shouldBe List(message -> false, serverClose -> false)
  }

  it should "send a Close frame emitted by the pipe exactly once" in {
    val userClose = WebSocketFrame.Close(4000, "user-initiated")

    val sent = sentFrames(Nil)(drainThen(userClose))

    sent shouldBe List(userClose -> false)
  }

  it should "not mark a Close frame following a non-final fragment as a continuation" in {
    val fragment = WebSocketFrame.Text("partial", finalFragment = false, None)
    val userClose = WebSocketFrame.Close(4000, "abort")

    val sent = sentFrames(Nil)(drainThen(fragment, userClose))

    sent shouldBe List(fragment -> false, userClose -> false)
  }

  it should "fall back to a default Close frame if neither the server nor the pipe provided one" in {
    val message = WebSocketFrame.text("hello")

    val sent = sentFrames(Nil)(drainThen(message))

    sent shouldBe List(message -> false, WebSocketFrame.close -> false)
  }
}
