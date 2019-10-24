package sttp.client.httpclient.monix

import monix.eval.Task
import sttp.client.SttpBackend
import sttp.client.httpclient.{HttpClientWebsocketTest, WebSocketHandler}
import sttp.client.monad.MonadError
import sttp.client.testing.ConvertToFuture
import monix.execution.Scheduler.Implicits.global
import sttp.client.impl.monix.{TaskMonadAsyncError, convertMonixTaskToFuture}

class HttpClientMonixWebsocketTest extends HttpClientWebsocketTest[Task] {
  override implicit val backend: SttpBackend[Task, _, WebSocketHandler] = HttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  override implicit val monadError: MonadError[Task] = TaskMonadAsyncError
}
