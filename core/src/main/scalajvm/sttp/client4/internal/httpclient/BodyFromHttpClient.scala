package sttp.client4.internal.httpclient

import sttp.capabilities.Streams
import sttp.client4._
import sttp.client4.internal._
import sttp.model.ResponseMetadata
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.{WebSocket, WebSocketFrame}

private[client4] trait BodyFromHttpClient[F[_], S, B] {
  val streams: Streams[S]
  implicit def monad: MonadError[F]
  def compileWebSocketPipe(ws: WebSocket[F], pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): F[Unit]

  def apply[T](
      response: Either[B, WebSocket[F]],
      responseAs: ResponseAsDelegate[T, _],
      responseMetadata: ResponseMetadata
  ): F[T] = bodyFromResponseAs(responseAs, responseMetadata, response)

  protected def bodyFromResponseAs: BodyFromResponseAs[F, B, WebSocket[F], streams.BinaryStream]

  protected def bodyFromWs[T](r: GenericWebSocketResponseAs[T, _], ws: WebSocket[F], meta: ResponseMetadata): F[T] =
    r match {
      case ResponseAsWebSocket(f) =>
        f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[T]](ws, meta).ensure(ws.close())
      case ResponseAsWebSocketUnsafe()     => ws.unit.asInstanceOf[F[T]]
      case ResponseAsWebSocketStream(_, p) =>
        compileWebSocketPipe(ws, p.asInstanceOf[streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
    }
}
