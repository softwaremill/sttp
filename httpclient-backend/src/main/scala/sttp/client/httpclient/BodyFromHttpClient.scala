package sttp.client.httpclient

import java.io.InputStream

import sttp.client.internal.FileHelpers
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
  Streams,
  WebSocketResponseAs
}
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocket
import sttp.client.monad.syntax._
import sttp.model.ws.WebSocketFrame

private[httpclient] trait BodyFromHttpClient[F[_], S] {
  val streams: Streams[S]
  implicit def monad: MonadError[F]
  def inputStreamToStream(is: InputStream): streams.BinaryStream
  def compileWebSocketPipe(ws: WebSocket[F], pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): F[Unit]

  def apply[T](
      responseBody: InputStream,
      r: ResponseAs[T, _],
      responseMetadata: ResponseMetadata,
      ws: Option[WebSocket[F]]
  ): F[T] =
    r match {
      case MappedResponseAs(raw, g) =>
        val nested = apply(responseBody, raw, responseMetadata, ws)
        nested.map(g(_, responseMetadata))

      case rfm: ResponseAsFromMetadata[T, _] =>
        apply(responseBody, rfm(responseMetadata), responseMetadata, ws)

      case ResponseAsStream(_, f) =>
        monad
          .eval(inputStreamToStream(responseBody))
          .flatMap(f.asInstanceOf[streams.BinaryStream => F[T]])
          .ensure(monad.eval(responseBody.close()))

      case _: ResponseAsStreamUnsafe[_, _] =>
        monad.eval(inputStreamToStream(responseBody).asInstanceOf[T])

      case IgnoreResponse =>
        monad.eval(responseBody.close())

      case ResponseAsByteArray =>
        monad.eval {
          try responseBody.readAllBytes()
          finally responseBody.close()
        }

      case ResponseAsFile(file) =>
        monad.eval {
          try {
            FileHelpers.saveFile(file.toFile, responseBody)
            file
          } finally responseBody.close()
        }

      case wsr: WebSocketResponseAs[T, _] =>
        ws match {
          case Some(webSocket) => bodyFromWs(wsr, webSocket)
          case None            => throw new IllegalStateException("Illegal WebSockets usage")
        }
    }

  private def bodyFromWs[T](r: WebSocketResponseAs[T, _], ws: WebSocket[F]): F[T] =
    r match {
      case ResponseAsWebSocket(f)      => f.asInstanceOf[WebSocket[F] => F[T]](ws).ensure(ws.close)
      case ResponseAsWebSocketUnsafe() => ws.unit.asInstanceOf[F[T]]
      case ResponseAsWebSocketStream(_, p) =>
        compileWebSocketPipe(ws, p.asInstanceOf[streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
    }
}
