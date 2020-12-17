package sttp.client3.httpclient

import sttp.capabilities.Streams
import sttp.client3.internal.BodyFromResponseAs
import sttp.client3.{
  ResponseAs,
  ResponseAsWebSocket,
  ResponseAsWebSocketStream,
  ResponseAsWebSocketUnsafe,
  ResponseMetadata,
  WebSocketResponseAs
}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.{WebSocket, WebSocketFrame}

private[httpclient] trait BodyFromHttpClient[F[_], S, B] {
  val streams: Streams[S]
  implicit def monad: MonadError[F]
  def compileWebSocketPipe(ws: WebSocket[F], pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): F[Unit]

  def apply[T](
      response: Either[B, WebSocket[F]],
      responseAs: ResponseAs[T, _],
      responseMetadata: ResponseMetadata
  ): F[T] = bodyFromResponseAs(responseAs, responseMetadata, response)

  protected def bodyFromResponseAs: BodyFromResponseAs[F, B, WebSocket[F], streams.BinaryStream]

  protected def bodyFromWs[T](r: WebSocketResponseAs[T, _], ws: WebSocket[F], meta: ResponseMetadata): F[T] =
    r match {
      case ResponseAsWebSocket(f) =>
        f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[T]](ws, meta).ensure(ws.close())
      case ResponseAsWebSocketUnsafe() => ws.unit.asInstanceOf[F[T]]
      case ResponseAsWebSocketStream(_, p) =>
        compileWebSocketPipe(ws, p.asInstanceOf[streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
    }
}
