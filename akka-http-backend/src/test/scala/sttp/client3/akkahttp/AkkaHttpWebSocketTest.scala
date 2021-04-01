package sttp.client3.akkahttp

import akka.stream.scaladsl.{Flow, Source}
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.websocket.{WebSocketConcurrentTest, WebSocketStreamingTest, WebSocketTest}
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.WebSocketFrame

import scala.concurrent.{ExecutionContext, Future}

class AkkaHttpWebSocketTest
    extends WebSocketTest[Future]
    with WebSocketStreamingTest[Future, AkkaStreams]
    with WebSocketConcurrentTest[Future] {
  override val streams: AkkaStreams = AkkaStreams
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override val backend: SttpBackend[Future, AkkaStreams with WebSockets] = AkkaHttpBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monad: MonadError[Future] = new FutureMonad

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): Flow[WebSocketFrame.Data[_], WebSocketFrame, Any] =
    Flow.fromFunction(f).mapConcat(_.toList): Flow[WebSocketFrame.Data[_], WebSocketFrame, Any]

  override def prepend(
      item: WebSocketFrame.Text
  )(to: Flow[WebSocketFrame.Data[_], WebSocketFrame, Any]): Flow[WebSocketFrame.Data[_], WebSocketFrame, Any] =
    to.prepend(Source(List(item)))

  override def fromTextPipe(function: String => WebSocketFrame): Flow[WebSocketFrame.Data[_], WebSocketFrame, Any] = {
    Flow[WebSocketFrame.Data[_]].collect { case tf: WebSocketFrame.Text => function(tf.payload) }
  }

  override def concurrently[T](fs: List[() => Future[T]]): Future[List[T]] = Future.sequence(fs.map(_()))
}
