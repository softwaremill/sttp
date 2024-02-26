package sttp.client4.fetch

import org.scalajs.dom.experimental.{BodyInit, Request => FetchRequest, Response => FetchResponse}
import sttp.client4._
import sttp.client4.internal.{ConvertFromFuture, NoStreams}
import sttp.client4.testing.WebSocketBackendStub
import sttp.monad.FutureMonad
import sttp.ws.WebSocket

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

class FetchBackend private (fetchOptions: FetchOptions, customizeRequest: FetchRequest => FetchRequest)(implicit
    ec: ExecutionContext
) extends AbstractFetchBackend[Future, Nothing](fetchOptions, customizeRequest, new FutureMonad()) {

  override val streams: NoStreams = NoStreams

  override protected def addCancelTimeoutHook[T](
      result: Future[T],
      cancel: () => Unit,
      cleanup: () => Unit
  ): Future[T] = {
    result.onComplete(_ => cleanup())
    result
  }

  override protected def handleStreamBody(s: Nothing): Future[js.UndefOr[BodyInit]] =
    // we have an instance of nothing - everything's possible!
    Future.successful(js.undefined)

  override protected def handleResponseAsStream(
      response: FetchResponse
  ): Future[Nothing] =
    throw new IllegalStateException("Future FetchBackend does not support streaming responses")

  override protected def compileWebSocketPipe(ws: WebSocket[Future], pipe: Nothing): Future[Unit] =
    throw new IllegalStateException("Future FetchBackend does not support streaming responses")

  override def convertFromFuture: ConvertFromFuture[Future] = ConvertFromFuture.future
}

object FetchBackend {
  def apply(
      fetchOptions: FetchOptions = FetchOptions.Default,
      customizeRequest: FetchRequest => FetchRequest = identity
  )(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackend[Future] =
    new FetchBackend(fetchOptions, customizeRequest)

  /** Create a stub backend for testing, which uses the [[Future]] response wrapper.
    *
    * See [[WebSocketBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackendStub[Future] =
    WebSocketBackendStub.asynchronousFuture
}
