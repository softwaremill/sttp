package sttp.client4

import sttp.client4.fetch.FetchBackend
import sttp.client4.testing.WebSocketBackendStub

import scala.concurrent.{ExecutionContext, Future}

object DefaultFutureBackend {

  /** Creates a default websocket-capable backend which uses [[Future]] to represent side effects, with the given
    * `options`. Currently based on [[FetchBackend]].
    */
  def apply()(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackend[Future] = FetchBackend()

  /** Create a stub backend for testing, which uses [[Future]] to represent side effects, and doesn't support streaming.
    *
    * See [[WebSocketBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackendStub[Future] =
    WebSocketBackendStub.asynchronousFuture
}
