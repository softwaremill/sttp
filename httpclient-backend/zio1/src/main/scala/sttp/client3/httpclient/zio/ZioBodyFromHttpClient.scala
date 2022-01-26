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
import zio.nio.Buffer
import zio.nio.channels.AsynchronousFileChannel
import zio.nio.file.Path
import zio.stream.{Stream, ZSink, ZStream}
import zio.{Managed, Task, ZIO}

import java.nio.ByteBuffer

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
          case Left(byteArray) => ZIO.succeed(Stream.fromIterable(byteArray))
          case Right(file) =>
            ZIO.succeed(
              for {
                fileChannel <- Stream.managed(
                  AsynchronousFileChannel.open(Path.fromJava(file.toPath), StandardOpenOption.READ)
                )
                bytes <- readAllBytes(fileChannel)
              } yield bytes
            )
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
          ZSink.managed(
            AsynchronousFileChannel
              .open(Path.fromJava(file.toPath), StandardOpenOption.WRITE, StandardOpenOption.CREATE): Managed[
              Exception,
              AsynchronousFileChannel
            ] // we need the upcast so that errors are properly inferred
          ) { fileChannel =>
            ZSink.foldChunksM(0L)(_ => true) { case (position, data) =>
              Buffer.byte(data).flatMap(buffer =>
                fileChannel
                  .write(buffer, position)
                  .map(bytesWritten => position + bytesWritten)
              )
            }
          }
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
    Stream.paginateChunkM(0L)(position =>
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
