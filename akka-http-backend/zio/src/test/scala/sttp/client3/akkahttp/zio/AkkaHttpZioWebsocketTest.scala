package sttp.client3.akkahttp.zio

import akka.stream.scaladsl.{Flow, Source}
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.zio.ZioStreams.Pipe
import sttp.client3._
import sttp.client3.akkahttp.{AkkaHttpBackend, AkkaHttpWebSocketTest}
import sttp.client3.impl.zio.{RIOMonadAsyncError, ZioTestBase, ZioWebSockets}
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.websocket.{WebSocketConcurrentTest, WebSocketStreamingTest, WebSocketTest}
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.WebSocketFrame
import zio.clock.Clock
import zio.duration._
import zio.stream._
import zio.{Schedule, Task, ZIO}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

class AkkaHttpZIOWebSocketBaseTest extends WebSocketTest[Task]
  with WebSocketStreamingTest[Task, ZioStreams]
  with WebSocketConcurrentTest[Task] with  ZioTestBase {
  override val streams: ZioStreams = ZioStreams
//  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val underlying = AkkaHttpBackend()
  override val backend: SttpBackend[Future, ZioStreams with WebSockets] = AkkaHttpClientZioBackend(underlying)
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture
  override implicit val monad: MonadError[Task] = new RIOMonadAsyncError

//  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] = {
//    ZIO.sleep(interval.toMillis.millis).andThen(f).retry(Schedule.recurs(attempts)).provideLayer(Clock.live)
//  }

  override def functionToPipe(
                               f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
                             ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    in => in.mapConcat(m => f(m).toList)

  override def fromTextPipe(
                             function: String => WebSocketFrame
                           ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    ZioWebSockets.fromTextPipe[Any](function)

  override def prepend(item: WebSocketFrame.Text)(
    to: ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    to.andThen(rest => ZStream(item) ++ rest)

  override def concurrently[T](fs: List[() => Task[T]]): Task[List[T]] = Task.collectAllPar(fs.map(_()))
}


class AkkaHttpZioWebsocketTest extends AkkaHttpZIOWebSocketBaseTest with ZioTestBase {
  //  override val streams: ZioStreams = ZioStreams
  //
  //  override val backend: SttpBackend[Task, WebSockets with ZioStreams] =
  //    runtime.unsafeRun(AsyncHttpClientZioBackend())
  //  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture
  //  override implicit val monad: MonadError[Task] = new RIOMonadAsyncError
  //
  //  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] = {
  //    ZIO.sleep(interval.toMillis.millis).andThen(f).retry(Schedule.recurs(attempts)).provideLayer(Clock.live)
  //  }
  //
  //  override def functionToPipe(
  //                               f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  //                             ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
  //    in => in.mapConcat(m => f(m).toList)
  //
  //  override def fromTextPipe(
  //                             function: String => WebSocketFrame
  //                           ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
  //    ZioWebSockets.fromTextPipe[Any](function)
  //
  //  override def prepend(item: WebSocketFrame.Text)(
  //    to: ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  //  ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
  //    to.andThen(rest => ZStream(item) ++ rest)
  //
  //  override def concurrently[T](fs: List[() => Task[T]]): Task[List[T]] = Task.collectAllPar(fs.map(_()))
  //}
}
