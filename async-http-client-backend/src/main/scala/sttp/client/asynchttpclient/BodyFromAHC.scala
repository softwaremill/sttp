package sttp.client.asynchttpclient

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer

import org.reactivestreams.Publisher
import sttp.capabilities.Streams
import sttp.client.{
  IgnoreResponse,
  MappedResponseAs,
  ResponseAs,
  ResponseAsByteArray,
  ResponseAsFile,
  ResponseAsFromMetadata,
  ResponseAsStream,
  ResponseAsStreamUnsafe,
  ResponseAsWebSocket,
  ResponseAsWebSocketStream,
  ResponseAsWebSocketUnsafe,
  ResponseMetadata,
  WebSocketResponseAs
}
import sttp.client.internal.FileHelpers
import sttp.client.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.monad.{Canceler, MonadAsyncError}
import sttp.monad.syntax._
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

  def compileWebSocketPipe(ws: WebSocket[F], pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): F[Unit]

  //

  def apply[TT](
      response: Either[Publisher[ByteBuffer], WebSocket[F]],
      responseAs: ResponseAs[TT, _],
      responseMetadata: ResponseMetadata,
      isSubscribed: () => Boolean
  ): F[TT] =
    (responseAs, response) match {
      case (MappedResponseAs(raw, g), _) =>
        val nested = apply(response, raw, responseMetadata, isSubscribed)
        nested.map(g(_, responseMetadata))

      case (rfm: ResponseAsFromMetadata[TT, _], _) =>
        apply(response, rfm(responseMetadata), responseMetadata, isSubscribed)

      case (ResponseAsStream(_, f), Left(p)) =>
        f.asInstanceOf[streams.BinaryStream => F[TT]](publisherToStream(p))
          .ensure(ignoreIfNotSubscribed(p, isSubscribed))

      case (_: ResponseAsStreamUnsafe[_, _], Left(p)) => monad.unit(publisherToStream(p).asInstanceOf[TT])

      case (IgnoreResponse, Left(p)) =>
        // getting the body and discarding it
        publisherToBytes(p).map(_ => ())

      case (ResponseAsByteArray, Left(p)) =>
        publisherToBytes(p).map(b => b) // adjusting type because ResponseAs is covariant

      case (ResponseAsFile(f), Left(p)) =>
        publisherToFile(p, f.toFile).map(_ => f)

      case (wsr: WebSocketResponseAs[TT, _], Right(ws)) =>
        bodyFromWs(wsr, ws)

      case (_: WebSocketResponseAs[_, _], Left(p)) =>
        ignoreIfNotSubscribed(p, isSubscribed).flatMap(_ =>
          monad.error(new NotAWebSocketException(responseMetadata.code))
        )

      case (_, Right(ws)) =>
        ws.close().flatMap(_ => monad.error(new GotAWebSocketException()))
    }

  private def bodyFromWs[TT](r: WebSocketResponseAs[TT, _], ws: WebSocket[F]): F[TT] =
    r match {
      case ResponseAsWebSocket(f)      => f.asInstanceOf[WebSocket[F] => F[TT]](ws).ensure(ws.close())
      case ResponseAsWebSocketUnsafe() => ws.unit.asInstanceOf[F[TT]]
      case ResponseAsWebSocketStream(_, p) =>
        compileWebSocketPipe(ws, p.asInstanceOf[streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
    }

  private def ignoreIfNotSubscribed(p: Publisher[ByteBuffer], isSubscribed: () => Boolean): F[Unit] = {
    if (isSubscribed()) monad.unit(()) else ignorePublisher(p)
  }

  private def ignorePublisher(p: Publisher[ByteBuffer]): F[Unit] = {
    monad.async { cb =>
      val subscriber = new IgnoreSubscriber(() => cb(Right(())), t => cb(Left(t)))
      p.subscribe(subscriber)
      Canceler(() => ())
    }
  }
}
