package sttp.client3.impl.cats

import cats.effect.syntax.all._
import cats.effect.{Async, Concurrent, ContextShift, Sync}
import org.scalajs.dom.experimental.{BodyInit, Request => FetchRequest, Response => FetchResponse}
import sttp.capabilities.WebSockets
import sttp.client3.internal.NoStreams
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{AbstractFetchBackend, ConvertFromFuture, FetchOptions, SttpBackend}

import scala.concurrent.Future
import scala.scalajs.js

class FetchCatsBackend[F[_]: Concurrent: ContextShift] private (
    fetchOptions: FetchOptions,
    customizeRequest: FetchRequest => FetchRequest
) extends AbstractFetchBackend[F, Nothing, WebSockets](fetchOptions, customizeRequest, new CatsMonadAsyncError) {

  override val streams: NoStreams = NoStreams

  override protected def addCancelTimeoutHook[T](result: F[T], cancel: () => Unit): F[T] = {
    val doCancel = Sync[F].delay(cancel())
    result.guarantee(doCancel)
  }

  override protected def handleStreamBody(s: Nothing): F[js.UndefOr[BodyInit]] = s

  override protected def handleResponseAsStream(response: FetchResponse): F[(Nothing, () => F[Unit])] = {
    throw new IllegalStateException("Future FetchBackend does not support streaming responses")
  }

  override def fromFuture: ConvertFromFuture[F] = new ConvertFromFuture[F] {
    override def apply[T](f: Future[T]): F[T] = Async.fromFuture(Sync[F].delay(f))
  }
}

object FetchCatsBackend {
  def apply[F[_]: Concurrent: ContextShift](
      fetchOptions: FetchOptions = FetchOptions.Default,
      customizeRequest: FetchRequest => FetchRequest = identity
  ): SttpBackend[F, WebSockets] =
    new FetchCatsBackend(fetchOptions, customizeRequest)

  /** Create a stub backend for testing, which uses the given [[F]] response wrapper.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Concurrent: ContextShift]: SttpBackendStub[F, Any] = SttpBackendStub(new CatsMonadAsyncError)
}
