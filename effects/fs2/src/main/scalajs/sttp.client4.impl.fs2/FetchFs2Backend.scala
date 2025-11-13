package sttp.client4.impl.fs2

import scala.scalajs.js.{Function1, Thenable, |}
import sttp.client4.testing.WebSocketStreamBackendStub
import fs2.Stream
import cats.syntax.all._
import cats.effect.syntax.all._
import sttp.client4.fetch.FetchOptions
import sttp.client4.fetch.AbstractFetchBackend
import sttp.capabilities.fs2.Fs2Streams
import cats.effect.kernel.Async
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.ws.WebSocketFrame.Data
import sttp.ws.WebSocket
import sttp.ws.WebSocketFrame
import scala.scalajs.js
import sttp.client4.internal.ConvertFromFuture
import scala.concurrent.Future
import sttp.client4.WebSocketStreamBackend
import scala.scalajs.js.typedarray._
import fs2.Chunk
import org.scalajs.dom.Request
import org.scalajs.dom.Response
import org.scalajs.dom.BodyInit

class FetchFs2Backend[F[_]: Async] private (fetchOptions: FetchOptions, customizeRequest: Request => Request)
    extends AbstractFetchBackend[F, Fs2Streams[F]](fetchOptions, customizeRequest, new CatsMonadAsyncError)
    with WebSocketStreamBackend[F, Fs2Streams[F]] {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override protected def addCancelTimeoutHook[T](result: F[T], cancel: () => Unit, cleanup: () => Unit): F[T] = {
    result
      .onCancel(Async[F].delay(cancel()).voidError)
      .guarantee(
        Async[F].delay(cleanup()).voidError
      )
  }

  override protected def compileWebSocketPipe(
      ws: WebSocket[F],
      pipe: streams.Pipe[Data[_], WebSocketFrame]
  ): F[Unit] = {
    Fs2WebSockets.handleThroughPipe[F](ws)(pipe)
  }

  override protected def handleResponseAsStream(response: Response): F[(streams.BinaryStream, () => F[Unit])] = {
    Async[F].delay {
      lazy val reader = response.body.getReader()
      def read() = Async[F].fromFuture(Async[F].delay(reader.read().toFuture))

      val cancel = Async[F].async[Unit] { callback =>
        Async[F]
          .delay {
            // copied from https://github.com/zio/zio/blob/fca6870f42b1d2b06ebf0e6c13b975bccea72f13/core/js/src/main/scala/zio/ZIOPlatformSpecific.scala#L58
            val onFulfilled: Function1[Unit, Unit | Thenable[Unit]] = new scala.Function1[Unit, Unit | Thenable[Unit]] {
              def apply(a: Unit): Unit | Thenable[Unit] = callback(Right(a))
            }
            val onRejected: Function1[Any, Unit | Thenable[Unit]] = new scala.Function1[Any, Unit | Thenable[Unit]] {
              def apply(e: Any): Unit | Thenable[Unit] =
                callback(Left(e match {
                  case t: Throwable => t
                  case _            => js.JavaScriptException(e)
                }))
            }
            reader.cancel("Response body reader cancelled").`then`[Unit](onFulfilled, js.defined(onRejected))
          }
          .as(None)
      }

      val stream = Stream
        .unfoldChunkEval(()) { case () =>
          read()
            .map { chunk =>
              if (chunk.done) {
                Option.empty
              } else {
                val bytes = new Int8Array(chunk.value.buffer).toArray
                Option((Chunk.array(bytes), ()))
              }
            }
        }
      (stream.onComplete(Stream.exec(cancel.voidError)), () => cancel.voidError)

    }
  }

  override protected def handleStreamBody(s: streams.BinaryStream): F[js.UndefOr[BodyInit]] = {
    s.chunks
      .fold(Chunk.empty[Byte])(_ ++ _)
      .compile
      .last
      .map {
        case None      => js.undefined
        case Some(res) => res.toArray.toTypedArray
      }
  }

  override def convertFromFuture: ConvertFromFuture[F] = {
    new ConvertFromFuture[F] {
      override def apply[T](f: Future[T]): F[T] = Async[F].fromFuture(monad.unit(f))
    }
  }
}

object FetchFs2Backend {

  def apply[F[_]: Async](
      fetchOptions: FetchOptions = FetchOptions.Default,
      customizeRequest: Request => Request = identity
  ): WebSocketStreamBackend[F, Fs2Streams[F]] = {
    new FetchFs2Backend[F](fetchOptions, customizeRequest)
  }

  /** Create a stub backend for testing, which uses the given [[F]] response wrapper
    *
    * See [[WebSocketStreamBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: WebSocketStreamBackendStub[F, Fs2Streams[F]] = WebSocketStreamBackendStub(
    new CatsMonadAsyncError[F]
  )

}
