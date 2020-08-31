package sttp.client.httpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client._
import sttp.client.impl.monix.{TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.monad.MonadError
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.{WebSocketStreamingTest, WebSocketTest}
import sttp.ws.WebSocketFrame

class HttpClientMonixWebSocketTest extends WebSocketTest[Task] with WebSocketStreamingTest[Task, MonixStreams] {
  implicit val backend: SttpBackend[Task, MonixStreams with WebSockets] =
    HttpClientMonixBackend().runSyncUnsafe()
  implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  implicit val monad: MonadError[Task] = TaskMonadAsyncError
  override val streams: MonixStreams = MonixStreams

  override def functionToPipe(
      initial: List[WebSocketFrame.Data[_]],
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] =
    in => Observable.fromIterable(initial) ++ in.concatMapIterable(m => f(m).toList)
}
