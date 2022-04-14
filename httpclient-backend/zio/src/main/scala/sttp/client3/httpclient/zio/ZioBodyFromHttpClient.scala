package sttp.client3.httpclient.zio

import java.nio.file.StandardOpenOption
import sttp.capabilities.zio.ZioStreams
import sttp.client3.httpclient.BodyFromHttpClient
import sttp.client3.impl.zio.{RIOMonadAsyncError, ZioWebSockets}
import sttp.client3.internal.{BodyFromResponseAs, SttpFile}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client3.WebSocketResponseAs
import sttp.model.ResponseMetadata
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}
import zio.nio.channels.AsynchronousFileChannel
import zio.nio.file.Path
import zio.stream.{Stream, ZSink, ZStream}
import zio.{Task, ZIO}

import java.io.IOException

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
      ): ZIO[Any, IOException, ZStream[Any, IOException, Byte]] = {
        replayableBody match {
          case Left(byteArray) => ZIO.succeed(Stream.fromIterable(byteArray))
          case Right(file) =>
            val fcIO = AsynchronousFileChannel.open(Path.fromJava(file.toPath), StandardOpenOption.READ)
            val streamIO = ZStream.scoped(fcIO).flatMap(readAllBytes)
            Task.succeed(streamIO)
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
          ZSink.unwrapScoped(
            AsynchronousFileChannel
              .open(Path.fromJava(file.toPath), StandardOpenOption.WRITE, StandardOpenOption.CREATE)
              .map { fileChannel =>
                ZSink.foldChunksZIO[Any, IOException, Byte, Long](0L)(_ => true) { case (position, data) =>
                  fileChannel
                    .writeChunk(data, position)
                    .map(_ => position + data.size)
                }
              }
          )
        })
        .as(file)

      override protected def regularAsStream(
          response: ZStream[Any, Throwable, Byte]
      ): Task[(ZStream[Any, Throwable, Byte], () => Task[Unit])] =
        Task.succeed((response, () => response.runDrain.catchAll(_ => ZIO.unit)))

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

  private def readAllBytes(fileChannel: AsynchronousFileChannel) = {
    val bufferSize = 4096
    Stream.paginateChunkZIO(0L)(position =>
      fileChannel
        .readChunk(bufferSize, position)
        .map {
          case data if data.isEmpty => data -> None
          case data                 => data -> Some(position + bufferSize)
        }
    )
  }

  override implicit def monad: MonadError[Task] = new RIOMonadAsyncError[Any]
}
