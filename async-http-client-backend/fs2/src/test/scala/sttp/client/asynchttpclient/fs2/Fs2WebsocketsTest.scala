package sttp.client.asynchttpclient.fs2

import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import fs2._
import org.scalatest.{AsyncFlatSpec, Matchers}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.client.monad.MonadError
import sttp.client.testing.{ConvertToFuture, TestHttpServer, ToFutureWrapper}
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.concurrent.Future

class Fs2WebsocketsTest extends AsyncFlatSpec with Matchers with TestHttpServer with ToFutureWrapper {
  implicit val backend: SttpBackend[IO, Nothing, WebSocketHandler] = AsyncHttpClientFs2Backend[IO]().unsafeRunSync()
  implicit val convertToFuture: ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }
  implicit val monad: MonadError[IO] = new CatsMonadAsyncError[IO]
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(implicitly)
  implicit lazy val timer: Timer[IO] = IO.timer(implicitly)

  def createHandler: Option[Int] => IO[WebSocketHandler[WebSocket[IO]]] = Fs2WebSocketHandler[IO](_)

  it should "run a simple echo pipe" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocketF(createHandler(None))
      .product(Ref.of[IO, Queue[String]](Queue.empty))
      .flatMap {
        case (response, results) =>
          Fs2WebSockets.handleSocketThroughTextPipe(response.result) { in =>
            val receive = in.evalMap(m => results.update(_.enqueue(m)))
            val send = Stream("Message 1".asRight, "Message 2".asRight, WebSocketFrame.close.asLeft)
            send merge receive.drain
          } >> results.get.map(_ should contain theSameElementsInOrderAs List("echo: Message 1", "echo: Message 2"))
      }
      .toFuture()
  }

  it should "run a simple read-only client" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/send_and_close")
      .openWebsocketF(createHandler(None))
      .product(Ref.of[IO, Queue[String]](Queue.empty))
      .flatMap {
        case (response, results) =>
          Fs2WebSockets.handleSocketThroughTextPipe(response.result) { in =>
            in.evalMap(m => results.update(_.enqueue(m))).drain
          } >> results.get.map(_ should contain theSameElementsInOrderAs List("test10", "test20"))
      }
      .toFuture()
  }
}
