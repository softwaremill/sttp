package sttp.client3.impl.monix

import monix.eval.Task
import sttp.capabilities
import sttp.client3.SttpBackend
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.websocket.WebSocketTest
import sttp.monad.MonadError

class FetchMonixWebSocketTest extends WebSocketTest[Task] {
  override val backend: SttpBackend[Task, capabilities.WebSockets] = FetchMonixBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override implicit def monad: MonadError[Task] = TaskMonadAsyncError
}
