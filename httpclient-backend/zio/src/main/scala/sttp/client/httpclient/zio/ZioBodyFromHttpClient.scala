package sttp.client.httpclient.zio

import sttp.capabilities.zio.BlockingZioStreams
import sttp.client.httpclient.BodyFromHttpClient
import sttp.client.impl.zio.{RIOMonadAsyncError, ZioWebSockets}
import sttp.client.internal.{BodyFromResponseAs, SttpFile}
import sttp.client.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client.{ResponseMetadata, WebSocketResponseAs}
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}
import zio.blocking.Blocking
import zio.stream.{Stream, ZSink, ZStream}
import zio.{Task, ZIO}

private[zio] class ZioBodyFromHttpClient
    extends BodyFromHttpClient[BlockingTask, BlockingZioStreams, BlockingZioStreams.BinaryStream] {
  override val streams: BlockingZioStreams = BlockingZioStreams

  override def compileWebSocketPipe(
      ws: WebSocket[BlockingTask],
      pipe: ZStream[Blocking, Throwable, WebSocketFrame.Data[_]] => ZStream[Blocking, Throwable, WebSocketFrame]
  ): BlockingTask[Unit] = ZioWebSockets.compilePipe(ws, pipe)

  override protected def bodyFromResponseAs: BodyFromResponseAs[BlockingTask, ZStream[
    Blocking,
    Throwable,
    Byte
  ], WebSocket[BlockingTask], ZStream[Blocking, Throwable, Byte]] =
    new BodyFromResponseAs[BlockingTask, BlockingZioStreams.BinaryStream, WebSocket[
      BlockingTask
    ], BlockingZioStreams.BinaryStream] {
      override protected def withReplayableBody(
          response: ZStream[Blocking, Throwable, Byte],
          replayableBody: Either[Array[Byte], SttpFile]
      ): Task[ZStream[Blocking, Throwable, Byte]] = {
        replayableBody match {
          case Left(byteArray) => ZIO.succeed(Stream.fromIterable(byteArray))
          case Right(file)     => ZIO.succeed(Stream.fromFile(file.toPath))
        }
      }

      override protected def regularIgnore(response: ZStream[Blocking, Throwable, Byte]): BlockingTask[Unit] =
        response.run(ZSink.drain)

      override protected def regularAsByteArray(
          response: ZStream[Blocking, Throwable, Byte]
      ): BlockingTask[Array[Byte]] = response.runCollect.map(_.toArray)

      override protected def regularAsFile(
          response: ZStream[Blocking, Throwable, Byte],
          file: SttpFile
      ): BlockingTask[SttpFile] = response.run(ZSink.fromFile(file.toPath)).as(file)

      override protected def regularAsStream(
          response: ZStream[Blocking, Throwable, Byte]
      ): Task[(ZStream[Blocking, Throwable, Byte], () => BlockingTask[Unit])] =
        Task.succeed((response, () => response.runDrain.catchAll(_ => ZIO.unit)))

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: WebSocket[BlockingTask]
      ): BlockingTask[T] = bodyFromWs(responseAs, ws)

      override protected def cleanupWhenNotAWebSocket(
          response: ZStream[Blocking, Throwable, Byte],
          e: NotAWebSocketException
      ): BlockingTask[Unit] = response.run(ZSink.drain)

      override protected def cleanupWhenGotWebSocket(
          response: WebSocket[BlockingTask],
          e: GotAWebSocketException
      ): BlockingTask[Unit] = response.close()
    }

  override implicit def monad: MonadError[BlockingTask] = new RIOMonadAsyncError[Blocking]
}
