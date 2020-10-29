package sttp.client3.httpclient.fs2

import cats.effect.Async
import fs2.io.file.Files
import fs2.{Pipe, Stream}
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.httpclient.BodyFromHttpClient
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.impl.fs2.Fs2WebSockets
import sttp.client3.internal.{BodyFromResponseAs, SttpFile}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client3.{ResponseMetadata, WebSocketResponseAs}
import sttp.monad.MonadError
import sttp.monad.syntax.{MonadErrorValueOps, _}
import sttp.ws.{WebSocket, WebSocketFrame}

private[fs2] class Fs2BodyFromHttpClient[F[_]: Async]()
    extends BodyFromHttpClient[F, Fs2Streams[F], Stream[F, Byte]] {
  override val streams: Fs2Streams[F] = Fs2Streams[F]
  override implicit val monad: MonadError[F] = new CatsMonadAsyncError[F]
  override def compileWebSocketPipe(
      ws: WebSocket[F],
      pipe: Pipe[F, WebSocketFrame.Data[_], WebSocketFrame]
  ): F[Unit] = Fs2WebSockets.handleThroughPipe(ws)(pipe)

  override protected def bodyFromResponseAs: BodyFromResponseAs[F, Stream[F, Byte], WebSocket[F], Stream[F, Byte]] =
    new BodyFromResponseAs[F, Stream[F, Byte], WebSocket[F], Stream[F, Byte]] {
      override protected def withReplayableBody(
          response: Stream[F, Byte],
          replayableBody: Either[Array[Byte], SttpFile]
      ): F[Stream[F, Byte]] = {
        replayableBody match {
          case Left(value)     => Stream.evalSeq[F, List, Byte](value.toList.unit).unit
          case Right(sttpFile) => Files[F].readAll(sttpFile.toPath, 32 * 1024).unit
        }
      }

      override protected def regularIgnore(response: Stream[F, Byte]): F[Unit] = response.compile.drain

      override protected def regularAsByteArray(response: Stream[F, Byte]): F[Array[Byte]] =
        response.chunkAll.compile.last.map(_.map(_.toArray).getOrElse(Array()))

      override protected def regularAsFile(response: Stream[F, Byte], file: SttpFile): F[SttpFile] = {
        response
          .through(Files[F].writeAll(file.toPath))
          .compile
          .drain
          .map(_ => file)
      }

      override protected def regularAsStream(response: Stream[F, Byte]): F[(Stream[F, Byte], () => F[Unit])] = {
        (
          response,
          // ignoring exceptions that occur when draining (i.e. the stream is already drained)
          () => response.compile.drain.handleError { case _: Exception => ().unit }
        ).unit
      }

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: WebSocket[F]
      ): F[T] = bodyFromWs(responseAs, ws)

      override protected def cleanupWhenNotAWebSocket(
          response: Stream[F, Byte],
          e: NotAWebSocketException
      ): F[Unit] = response.compile.drain

      override protected def cleanupWhenGotWebSocket(response: WebSocket[F], e: GotAWebSocketException): F[Unit] =
        response.close()
    }
}
