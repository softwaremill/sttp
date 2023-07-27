package sttp.client4.testing.websocket

import org.scalatest.Suite
import org.scalatest.flatspec.AsyncFlatSpecLike
import sttp.client4._
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.HttpTest.wsEndpoint
import sttp.client4.ws.async._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.WebSocket

trait WebSocketConcurrentTest[F[_]] { outer: Suite with AsyncFlatSpecLike with WebSocketTest[F] =>
  implicit def monad: MonadError[F]
  implicit val convertToFuture: ConvertToFuture[F]

  it should "send & receive messages concurrently" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { (ws: WebSocket[F]) =>
        val n = 32
        val tasks = List.fill(n)(() => ws.sendText("test"))

        for {
          _ <- concurrently(tasks)
          r <- sequence(List.fill(n)(() => ws.receiveText()))
          _ <- ws.close()
        } yield r shouldBe List.fill(n)("echo: test")
      })
      .send(backend)
      .map(_ => succeed)
      .toFuture()
  }

  def concurrently[T](fs: List[() => F[T]]): F[List[T]]
  def sequence[T](fs: List[() => F[T]]): F[List[T]] = fs match {
    case Nil          => (Nil: List[T]).unit
    case head :: tail => head().flatMap(h => sequence(tail).map(h :: _))
  }
}
