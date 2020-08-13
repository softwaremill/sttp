package sttp.client.httpclient

import java.io.InputStream

import sttp.capabilities.Streams
import sttp.client.internal.FileHelpers
import sttp.client.ws.{GotAWebSocketException, NotAWebSocketException}
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
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.{WebSocket, WebSocketFrame}

private[httpclient] trait BodyFromHttpClient[F[_], S] {
  val streams: Streams[S]
  implicit def monad: MonadError[F]
  def inputStreamToStream(is: InputStream): streams.BinaryStream
  def compileWebSocketPipe(ws: WebSocket[F], pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): F[Unit]

  def apply[T](
      response: Either[InputStream, WebSocket[F]],
      responseAs: ResponseAs[T, _],
      responseMetadata: ResponseMetadata
  ): F[T] =
    (responseAs, response) match {
      case (MappedResponseAs(raw, g), _) =>
        val nested = apply(response, raw, responseMetadata)
        nested.map(g(_, responseMetadata))

      case (rfm: ResponseAsFromMetadata[T, _], _) =>
        apply(response, rfm(responseMetadata), responseMetadata)

      case (ResponseAsStream(_, f), Left(is)) =>
        monad
          .eval(inputStreamToStream(is))
          .flatMap(f.asInstanceOf[streams.BinaryStream => F[T]])
          .ensure(monad.eval(is.close()))

      case (_: ResponseAsStreamUnsafe[_, _], Left(is)) =>
        monad.eval(inputStreamToStream(is).asInstanceOf[T])

      case (IgnoreResponse, Left(is)) =>
        monad.eval(is.close())

      case (ResponseAsByteArray, Left(is)) =>
        monad.eval {
          try is.readAllBytes()
          finally is.close()
        }

      case (ResponseAsFile(file), Left(is)) =>
        monad.eval {
          try {
            FileHelpers.saveFile(file.toFile, is)
            file
          } finally is.close()
        }

      case (wsr: WebSocketResponseAs[T, _], Right(ws)) =>
        bodyFromWs(wsr, ws)

      case (_: WebSocketResponseAs[T, _], Left(is)) =>
        is.close()
        monad.error(new NotAWebSocketException(responseMetadata.code))

      case (_, Right(ws)) =>
        ws.close().flatMap(_ => monad.error(new GotAWebSocketException()))
    }

  private def bodyFromWs[T](r: WebSocketResponseAs[T, _], ws: WebSocket[F]): F[T] =
    r match {
      case ResponseAsWebSocket(f)      => f.asInstanceOf[WebSocket[F] => F[T]](ws).ensure(ws.close())
      case ResponseAsWebSocketUnsafe() => ws.unit.asInstanceOf[F[T]]
      case ResponseAsWebSocketStream(_, p) =>
        compileWebSocketPipe(ws, p.asInstanceOf[streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
    }
}
