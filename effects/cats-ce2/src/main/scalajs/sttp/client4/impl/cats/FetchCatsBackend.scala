package sttp.client4.impl.cats

import cats.effect.syntax.all._
import cats.effect.{Async, Concurrent, ContextShift, Sync}
import org.scalajs.dom.experimental.{BodyInit, Request => FetchRequest, Response => FetchResponse}
import sttp.client4.internal.{ConvertFromFuture, NoStreams}
import sttp.client4.testing.WebSocketBackendStub
import sttp.client4.{AbstractFetchBackend, FetchOptions, WebSocketBackend}
import sttp.ws.WebSocket

import scala.concurrent.Future
import scala.scalajs.js

class FetchCatsBackend[F[_]: Concurrent: ContextShift] private (
    fetchOptions: FetchOptions,
    customizeRequest: FetchRequest => FetchRequest
) extends AbstractFetchBackend[F, Nothing](fetchOptions, customizeRequest, new CatsMonadAsyncError) {

  override val streams: NoStreams = NoStreams

  override protected def addCancelTimeoutHook[T](result: F[T], cancel: () => Unit): F[T] = {
    val doCancel = Sync[F].delay(cancel())
    result.guarantee(doCancel)
  }

  override protected def handleStreamBody(s: Nothing): F[js.UndefOr[BodyInit]] = s

  override protected def handleResponseAsStream(response: FetchResponse): F[(Nothing, () => F[Unit])] =
    throw new IllegalStateException("FetchCatsBackend does not support streaming responses")

  override protected def compileWebSocketPipe(ws: WebSocket[F], pipe: Nothing): F[Unit] =
    throw new IllegalStateException("FetchCatsBackend does not support streaming responses")

  override def convertFromFuture: ConvertFromFuture[F] = new ConvertFromFuture[F] {
    override def apply[T](f: Future[T]): F[T] = Async.fromFuture(monad.unit(f))
  }
}

object FetchCatsBackend {
  def apply[F[_]: Concurrent: ContextShift](
      fetchOptions: FetchOptions = FetchOptions.Default,
      customizeRequest: FetchRequest => FetchRequest = identity
  ): WebSocketBackend[F] =
    new FetchCatsBackend(fetchOptions, customizeRequest)

  /** Create a stub backend for testing, which uses the given [[F]] response wrapper.
    *
    * See [[WebSocketBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Concurrent: ContextShift]: WebSocketBackendStub[F] = WebSocketBackendStub(new CatsMonadAsyncError)
}
