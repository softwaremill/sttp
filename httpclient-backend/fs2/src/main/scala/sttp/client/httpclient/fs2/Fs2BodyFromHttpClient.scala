package sttp.client.httpclient.fs2

import cats.effect.{Blocker, ConcurrentEffect, ContextShift}
import fs2.{Pipe, Stream}
import sttp.capabilities.fs2.Fs2Streams
import sttp.client.httpclient.BodyFromHttpClient
import sttp.client.impl.cats.implicits
import sttp.client.impl.fs2.Fs2WebSockets
import sttp.client.internal.{BodyFromResponseAs, SttpFile}
import sttp.client.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client.{ResponseMetadata, WebSocketResponseAs}
import sttp.monad.MonadError
import sttp.monad.syntax.{MonadErrorValueOps, _}
import sttp.ws.{WebSocket, WebSocketFrame}

private[fs2] class Fs2BodyFromHttpClient[F[_]: ConcurrentEffect: ContextShift]
    extends BodyFromHttpClient[F, Fs2Streams[F], Stream[F, Byte]] {
  override val streams: Fs2Streams[F] = Fs2Streams[F]
  override implicit def monad: MonadError[F] = implicits.asyncMonadError[F]
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
          case Left(value) => Stream.evalSeq[F,List,Byte](value.toList.unit).unit
          case Right(sttpFile) =>
            Stream
              .resource(Blocker[F])
              .flatMap { blocker => fs2.io.file.readAll(sttpFile.toPath, blocker, 32 * 1024) }
              .unit
        }
      }

      override protected def regularIgnore(response: Stream[F, Byte]): F[Unit] = response.compile.drain

      override protected def regularAsByteArray(response: Stream[F, Byte]): F[Array[Byte]] =
        response.compile.toList.map(_.toArray) //TODO collect directly to array

      override protected def regularAsFile(response: Stream[F, Byte], file: SttpFile): F[SttpFile] = {
        Stream
          .resource(Blocker[F])
          .flatMap { blocker => response.through(fs2.io.file.writeAll(file.toPath, blocker)) }
          .compile
          .drain
          .map(_ => file)
      }

      override protected def regularAsStream(response: Stream[F, Byte]): F[(Stream[F, Byte], () => F[Unit])] = {
        monad.eval(response -> { () => monad.unit() }) //TODO do we have to drain stream here?
      }

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: WebSocket[F]
      ): F[T] = bodyFromWs(responseAs,ws)

      override protected def cleanupWhenNotAWebSocket(
          response: Stream[F, Byte],
          e: NotAWebSocketException
      ): F[Unit] = response.compile.drain

      override protected def cleanupWhenGotWebSocket(response: WebSocket[F], e: GotAWebSocketException): F[Unit] =
        response.close()
    }
}
