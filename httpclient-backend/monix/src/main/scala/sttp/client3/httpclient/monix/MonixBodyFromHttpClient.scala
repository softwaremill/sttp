package sttp.client3.httpclient.monix

import java.nio.ByteBuffer
import java.nio.file.StandardOpenOption

import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.{Consumer, Observable}
import sttp.capabilities.monix.MonixStreams
import sttp.client3.httpclient.BodyFromHttpClient
import sttp.client3.impl.monix.MonixWebSockets
import sttp.client3.internal.{BodyFromResponseAs, RichByteBuffer, SttpFile}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client3.{ResponseMetadata, WebSocketResponseAs}
import sttp.ws.{WebSocket, WebSocketFrame}
import monix.nio.file._

trait MonixBodyFromHttpClient extends BodyFromHttpClient[Task, MonixStreams, MonixStreams.BinaryStream] {
  override val streams: MonixStreams = MonixStreams
  implicit def scheduler: Scheduler

  override def compileWebSocketPipe(
      ws: WebSocket[Task],
      pipe: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
  ): Task[Unit] =
    MonixWebSockets.compilePipe(ws, pipe)

  override protected def bodyFromResponseAs
      : BodyFromResponseAs[Task, Observable[ByteBuffer], WebSocket[Task], Observable[ByteBuffer]] = {
    new BodyFromResponseAs[Task, MonixStreams.BinaryStream, WebSocket[Task], MonixStreams.BinaryStream]() {
      override protected def withReplayableBody(
          response: Observable[ByteBuffer],
          replayableBody: Either[Array[Byte], SttpFile]
      ): Task[Observable[ByteBuffer]] = {
        replayableBody match {
          case Left(value) => Task.pure(Observable.now(ByteBuffer.wrap(value)))
          case Right(file) => Task.pure(readAsync(file.toPath, 32 * 1024).map(ByteBuffer.wrap))
        }
      }

      override protected def regularIgnore(response: Observable[ByteBuffer]): Task[Unit] =
        response.consumeWith(Consumer.complete)

      override protected def regularAsByteArray(response: Observable[ByteBuffer]): Task[Array[Byte]] =
        response.consumeWith(Consumer.foldLeft(Array.emptyByteArray)((acc, item) => acc ++ item.safeRead()))

      override protected def regularAsFile(response: Observable[ByteBuffer], file: SttpFile): Task[SttpFile] =
        response
          .map(_.safeRead())
          .consumeWith(writeAsync(file.toPath, Seq(StandardOpenOption.WRITE, StandardOpenOption.CREATE)))
          .as(file)

      override protected def regularAsStream(
          response: Observable[ByteBuffer]
      ): Task[(Observable[ByteBuffer], () => Task[Unit])] =
        Task.pure((response, () => response.consumeWith(Consumer.complete).onErrorFallbackTo(Task.unit)))

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: WebSocket[Task]
      ): Task[T] = bodyFromWs(responseAs, ws)

      override protected def cleanupWhenNotAWebSocket(
          response: Observable[ByteBuffer],
          e: NotAWebSocketException
      ): Task[Unit] = response.consumeWith(Consumer.complete)

      override protected def cleanupWhenGotWebSocket(response: WebSocket[Task], e: GotAWebSocketException): Task[Unit] =
        response.close()
    }
  }
}
