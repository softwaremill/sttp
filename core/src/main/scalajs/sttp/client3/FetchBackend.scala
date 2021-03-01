package sttp.client3

import org.scalajs.dom.experimental.{BodyInit, Request => FetchRequest, Response => FetchResponse}
import sttp.capabilities.WebSockets
import sttp.client3.internal.NoStreams
import sttp.client3.testing.SttpBackendStub
import sttp.monad.FutureMonad

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

class FetchBackend private (fetchOptions: FetchOptions, customizeRequest: FetchRequest => FetchRequest)(implicit
    ec: ExecutionContext
) extends AbstractFetchBackend[Future, Nothing, WebSockets](fetchOptions, customizeRequest, new FutureMonad()) {

  override val streams: NoStreams = NoStreams

  override protected def addCancelTimeoutHook[T](result: Future[T], cancel: () => Unit): Future[T] = {
    result.onComplete(_ => cancel())
    result
  }

  override protected def handleStreamBody(s: Nothing): Future[js.UndefOr[BodyInit]] = {
    // we have an instance of nothing - everything's possible!
    Future.successful(js.undefined)
  }

  override protected def handleResponseAsStream(
      response: FetchResponse
  ): Future[Nothing] = {
    throw new IllegalStateException("Future FetchBackend does not support streaming responses")
  }

  override def convertFromFuture: ConvertFromFuture[Future] = ConvertFromFuture.future
}

object FetchBackend {
  def apply(
      fetchOptions: FetchOptions = FetchOptions.Default,
      customizeRequest: FetchRequest => FetchRequest = identity
  )(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, WebSockets] =
    new FetchBackend(fetchOptions, customizeRequest)

  /** Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackendStub[Future, Any] =
    SttpBackendStub(new FutureMonad())
}
