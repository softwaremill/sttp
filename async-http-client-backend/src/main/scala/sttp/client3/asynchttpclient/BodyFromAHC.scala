package sttp.client3.asynchttpclient

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer

import org.reactivestreams.Publisher
import sttp.capabilities.Streams
import sttp.client3.internal.{BodyFromResponseAs, FileHelpers, SttpFile, nonReplayableBody}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client3.{
  ResponseAs,
  ResponseAsWebSocket,
  ResponseAsWebSocketStream,
  ResponseAsWebSocketUnsafe,
  ResponseMetadata,
  WebSocketResponseAs
}
import sttp.monad.syntax._
import sttp.monad.{Canceler, MonadAsyncError}
import sttp.ws.{WebSocket, WebSocketFrame}

private[asynchttpclient] trait BodyFromAHC[F[_], S] {
  val streams: Streams[S]
  implicit def monad: MonadAsyncError[F]

  def publisherToStream(p: Publisher[ByteBuffer]): streams.BinaryStream

  def publisherToBytes(p: Publisher[ByteBuffer]): F[Array[Byte]] = {
    monad.async { cb =>
      def success(r: ByteBuffer): Unit = cb(Right(r.array()))
      def error(t: Throwable): Unit = cb(Left(t))

      val subscriber = new SimpleSubscriber(success, error)
      p.subscribe(subscriber)

      Canceler(() => subscriber.cancel())
    }
  }

  def publisherToFile(p: Publisher[ByteBuffer], f: File): F[Unit] = {
    publisherToBytes(p).map(bytes => FileHelpers.saveFile(f, new ByteArrayInputStream(bytes)))
  }

  def bytesToPublisher(b: Array[Byte]): F[Publisher[ByteBuffer]] =
    (new SingleElementPublisher(ByteBuffer.wrap(b)): Publisher[ByteBuffer]).unit

  def fileToPublisher(f: File): F[Publisher[ByteBuffer]] =
    (new SingleElementPublisher[ByteBuffer](ByteBuffer.wrap(FileHelpers.readFile(f))): Publisher[ByteBuffer]).unit

  def compileWebSocketPipe(ws: WebSocket[F], pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): F[Unit]

  //

  private def bodyFromResponseAs(isSubscribed: () => Boolean) =
    new BodyFromResponseAs[F, Publisher[ByteBuffer], WebSocket[F], streams.BinaryStream] {
      override protected def withReplayableBody(
          response: Publisher[ByteBuffer],
          replayableBody: Either[Array[Byte], SttpFile]
      ): F[Publisher[ByteBuffer]] =
        replayableBody match {
          case Left(byteArray) => bytesToPublisher(byteArray)
          case Right(file)     => fileToPublisher(file.toFile)
        }

      override protected def regularIgnore(response: Publisher[ByteBuffer]): F[Unit] = {
        // getting the body and discarding it
        publisherToBytes(response).map(_ => ((), nonReplayableBody))
      }

      override protected def regularAsByteArray(response: Publisher[ByteBuffer]): F[Array[Byte]] =
        publisherToBytes(response)

      override protected def regularAsFile(response: Publisher[ByteBuffer], file: SttpFile): F[SttpFile] =
        publisherToFile(response, file.toFile).map(_ => file)

      override protected def regularAsStream(
          response: Publisher[ByteBuffer]
      ): F[(streams.BinaryStream, () => F[Unit])] =
        (publisherToStream(response), () => ignoreIfNotSubscribed(response, isSubscribed)).unit

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: WebSocket[F]
      ): F[T] = bodyFromWs(responseAs, ws, meta)

      override protected def cleanupWhenNotAWebSocket(
          response: Publisher[ByteBuffer],
          e: NotAWebSocketException
      ): F[Unit] = ignoreIfNotSubscribed(response, isSubscribed)

      override protected def cleanupWhenGotWebSocket(response: WebSocket[F], e: GotAWebSocketException): F[Unit] =
        response.close()
    }

  def apply[TT](
      response: Either[Publisher[ByteBuffer], WebSocket[F]],
      responseAs: ResponseAs[TT, _],
      responseMetadata: ResponseMetadata,
      isSubscribed: () => Boolean
  ): F[TT] = bodyFromResponseAs(isSubscribed)(responseAs, responseMetadata, response)

  private def bodyFromWs[TT](r: WebSocketResponseAs[TT, _], ws: WebSocket[F], meta: ResponseMetadata): F[TT] =
    r match {
      case ResponseAsWebSocket(f) =>
        f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[TT]].apply(ws, meta).ensure(ws.close())
      case ResponseAsWebSocketUnsafe() => ws.unit.asInstanceOf[F[TT]]
      case ResponseAsWebSocketStream(_, p) =>
        compileWebSocketPipe(ws, p.asInstanceOf[streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
    }

  private def ignoreIfNotSubscribed(p: Publisher[ByteBuffer], isSubscribed: () => Boolean): F[Unit] = {
    monad.eval(isSubscribed()).flatMap(is => if (is) monad.unit(()) else ignorePublisher(p))
  }

  private def ignorePublisher(p: Publisher[ByteBuffer]): F[Unit] = {
    monad.async { cb =>
      val subscriber = new IgnoreSubscriber(() => cb(Right(())), t => cb(Left(t)))
      p.subscribe(subscriber)
      Canceler(() => ())
    }
  }
}
