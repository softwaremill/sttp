package sttp.client3.httpclient.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client3.WebSocketResponseAs
import sttp.client3.impl.zio.{RIOMonadAsyncError, ZioWebSockets}
import sttp.client3.internal.httpclient.BodyFromHttpClient
import sttp.client3.internal.{BodyFromResponseAs, SttpFile}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.model.ResponseMetadata
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}
import zio.stream.{ZSink, ZStream}
import zio.{Task, ZIO}

import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption

private[zio] class ZioBodyFromHttpClient extends BodyFromHttpClient[Task, ZioStreams, ZioStreams.BinaryStream] {
  override val streams: ZioStreams = ZioStreams

  override def compileWebSocketPipe(
      ws: WebSocket[Task],
      pipe: ZStream[Any, Throwable, WebSocketFrame.Data[_]] => ZStream[Any, Throwable, WebSocketFrame]
  ): Task[Unit] = ZioWebSockets.compilePipe(ws, pipe)

  override protected def bodyFromResponseAs: BodyFromResponseAs[Task, ZStream[
    Any,
    Throwable,
    Byte
  ], WebSocket[Task], ZStream[Any, Throwable, Byte]] =
    new BodyFromResponseAs[Task, ZioStreams.BinaryStream, WebSocket[
      Task
    ], ZioStreams.BinaryStream] {
      override protected def withReplayableBody(
          response: ZStream[Any, Throwable, Byte],
          replayableBody: Either[Array[Byte], SttpFile]
      ): Task[ZStream[Any, Throwable, Byte]] = {
        replayableBody match {
          case Left(byteArray) => ZIO.succeed(ZStream.fromIterable(byteArray))
          case Right(file)     => ZIO.succeed(ZStream.fromPath(file.toPath))
        }
      }

      override protected def regularIgnore(response: ZStream[Any, Throwable, Byte]): Task[Unit] =
        response.run(ZSink.drain)

      override protected def regularAsByteArray(
          response: ZStream[Any, Throwable, Byte]
      ): Task[Array[Byte]] = response.runCollect.map(_.toArray)

      override protected def regularAsFile(
          response: ZStream[Any, Throwable, Byte],
          file: SttpFile
      ): Task[SttpFile] = response
        .run({
          val options = Set[OpenOption](StandardOpenOption.WRITE, StandardOpenOption.CREATE)
          ZSink.fromPath(file.toPath, options = options)
        })
        .as(file)

      override protected def regularAsStream(
          response: ZStream[Any, Throwable, Byte]
      ): Task[(ZStream[Any, Throwable, Byte], () => Task[Unit])] =
        ZIO.succeed((response, () => response.runDrain.catchAll(_ => ZIO.unit)))

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: WebSocket[Task]
      ): Task[T] = bodyFromWs(responseAs, ws, meta)

      override protected def cleanupWhenNotAWebSocket(
          response: ZStream[Any, Throwable, Byte],
          e: NotAWebSocketException
      ): Task[Unit] = response.run(ZSink.drain)

      override protected def cleanupWhenGotWebSocket(
          response: WebSocket[Task],
          e: GotAWebSocketException
      ): Task[Unit] = response.close()
    }

  override implicit def monad: MonadError[Task] = new RIOMonadAsyncError[Any]
}
