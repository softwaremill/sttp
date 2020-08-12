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
      p: Publisher[ByteBuffer],
      r: ResponseAs[TT, _],
      responseMetadata: ResponseMetadata,
      isSubscribed: () => Boolean,
      ws: Option[WebSocket[F]]
  ): F[TT] =
    r match {
      case MappedResponseAs(raw, g) =>
        val nested = apply(p, raw, responseMetadata, isSubscribed, ws)
        nested.map(g(_, responseMetadata))
      case rfm: ResponseAsFromMetadata[TT, _] => apply(p, rfm(responseMetadata), responseMetadata, isSubscribed, ws)

      case ResponseAsStream(_, f) =>
        f.asInstanceOf[streams.BinaryStream => F[TT]](publisherToStream(p))
          .ensure(ignoreIfNotSubscribed(p, isSubscribed))
      case _: ResponseAsStreamUnsafe[_, _] => monad.unit(publisherToStream(p).asInstanceOf[TT])

      case IgnoreResponse =>
        // getting the body and discarding it
        publisherToBytes(p).map(_ => ())

      case ResponseAsByteArray =>
        publisherToBytes(p).map(b => b) // adjusting type because ResponseAs is covariant

      case ResponseAsFile(f) =>
        publisherToFile(p, f.toFile).map(_ => f)

      case wsr: WebSocketResponseAs[TT, _] =>
        ws match {
          case Some(webSocket) => bodyFromWs(wsr, webSocket)
          case None            => throw new IllegalStateException("Illegal WebSockets usage")
        }
    }

  private def bodyFromWs[TT](r: WebSocketResponseAs[TT, _], ws: WebSocket[F]): F[TT] =
    r match {
      case ResponseAsWebSocket(f)      => f.asInstanceOf[WebSocket[F] => F[TT]](ws).ensure(ws.close)
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
