package sttp.client.akkahttp

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.client._
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.{WebSocketStreamingTest, WebSocketTest}
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.WebSocketFrame

import scala.concurrent.{ExecutionContext, Future}

class AkkaHttpWebSocketTest extends WebSocketTest[Future] with WebSocketStreamingTest[Future, AkkaStreams] {
  override val streams: AkkaStreams = AkkaStreams
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override val backend: SttpBackend[Future, AkkaStreams with WebSockets] = AkkaHttpBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monad: MonadError[Future] = new FutureMonad

  override def functionToPipe(
      initial: List[WebSocketFrame.Data[_]],
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): Flow[WebSocketFrame.Data[_], WebSocketFrame, Any] = {
    val initialSource = Source(initial)
    val mainFlow = Flow.fromFunction(f).mapConcat(_.toList): Flow[WebSocketFrame.Data[_], WebSocketFrame, Any]
    mainFlow.prepend(initialSource)
  }

  override def prepend(
      item: WebSocketFrame.Text
  )(to: Flow[WebSocketFrame.Data[_], WebSocketFrame, Any]): Flow[WebSocketFrame.Data[_], WebSocketFrame, Any] =
    to.prepend(Source(List(item)))

  override def fromTextPipe(function: String => WebSocketFrame): Flow[WebSocketFrame.Data[_], WebSocketFrame, Any] = {
    Flow[WebSocketFrame.Data[_]].collect { case tf: WebSocketFrame.Text => function(tf.payload) }
  }
}
