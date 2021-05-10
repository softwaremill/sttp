package sttp.client3.armeria

import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream.StreamMessage
import io.netty.util.concurrent.EventExecutor
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import sttp.capabilities.Streams
import sttp.client3.WebSocketResponseAs
import sttp.client3.armeria.AbstractArmeriaBackend.{RightUnit, noopCanceler}
import sttp.client3.internal.{BodyFromResponseAs, SttpFile}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.model.ResponseMetadata
import sttp.monad.syntax._
import sttp.monad.{Canceler, MonadAsyncError}

private[armeria] trait BodyFromStreamMessage[F[_], S] {
  val streams: Streams[S]

  implicit def monad: MonadAsyncError[F]

  def publisherToStream(streamMessage: StreamMessage[HttpData]): streams.BinaryStream

  def publisherToBytes(
      streamMessage: StreamMessage[HttpData],
      executor: EventExecutor,
      aggregatorRef: AtomicReference[StreamMessageAggregator]
  ): F[Array[Byte]] = {
    var aggregator = new StreamMessageAggregator
    if (aggregatorRef.compareAndSet(null, aggregator)) {
      // Armeria's Publisher can only subscribe once.
      streamMessage.subscribe(aggregator, executor)
    } else {
      aggregator = aggregatorRef.get()
    }

    monad.async(cb => {
      aggregator.future.handle((data: HttpData, cause: Throwable) => {
        if (cause == null) {
          val array = data.array()
          cb(Right(array))
        } else {
          cb(Left(cause))
        }
        null
      })
      Canceler(() => streamMessage.abort())
    })
  }

  def publisherToFile(
      p: StreamMessage[HttpData],
      f: File,
      executor: EventExecutor
  ): F[Unit] = {
    val writer = new AsyncFileWriter(p, f.toPath, executor)
    monad.async[Unit](cb => {
      writer
        .whenComplete()
        .handle((_: Void, cause: Throwable) => {
          if (cause != null) {
            cb(Left(cause))
          } else {
            cb(RightUnit)
          }
          null
        })
      noopCanceler
    })
  }

  def bytesToPublisher(b: Array[Byte]): F[StreamMessage[HttpData]] = {
    StreamMessage.of(Array(HttpData.wrap(b)): _*).unit
  }

  def pathToPublisher(f: Path): F[StreamMessage[HttpData]] = {
    StreamMessage.of(f).unit
  }

  def apply(
      executor: EventExecutor,
      aggregatorRef: AtomicReference[StreamMessageAggregator]
  ): BodyFromResponseAs[F, StreamMessage[HttpData], Nothing, streams.BinaryStream] =
    new BodyFromResponseAs[F, StreamMessage[HttpData], Nothing, streams.BinaryStream] {
      override protected def withReplayableBody(
          response: StreamMessage[HttpData],
          replayableBody: Either[Array[Byte], SttpFile]
      ): F[StreamMessage[HttpData]] =
        replayableBody match {
          case Left(byteArray) => bytesToPublisher(byteArray)
          case Right(file)     => pathToPublisher(file.toPath)
        }

      override protected def regularIgnore(response: StreamMessage[HttpData]): F[Unit] =
        monad.eval(response.abort())

      override protected def regularAsByteArray(response: StreamMessage[HttpData]): F[Array[Byte]] =
        publisherToBytes(response, executor, aggregatorRef)

      override protected def regularAsFile(response: StreamMessage[HttpData], file: SttpFile): F[SttpFile] =
        publisherToFile(response, file.toFile, executor).map(_ => file)

      override protected def regularAsStream(
          response: StreamMessage[HttpData]
      ): F[(streams.BinaryStream, () => F[Unit])] = {
        (publisherToStream(response), () => monad.eval(response.abort())).unit
      }

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: Nothing
      ): F[T] = ws

      override protected def cleanupWhenNotAWebSocket(
          response: StreamMessage[HttpData],
          e: NotAWebSocketException
      ): F[Unit] = monad.unit(response.abort())

      override protected def cleanupWhenGotWebSocket(response: Nothing, e: GotAWebSocketException): F[Unit] =
        response
    }
}
