package sttp.client.okhttp

import java.io.InputStream

import sttp.capabilities.Streams
import sttp.client.internal.{FileHelpers, toByteArray}
import sttp.client.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.monad.MonadError
import sttp.monad.syntax._
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
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.util.Try

private[okhttp] trait BodyFromOkHttp[F[_], S] {
  val streams: Streams[S]
  implicit def monad: MonadError[F]

  def responseBodyToStream(inputStream: InputStream): streams.BinaryStream

  private def fromWs[TT](r: WebSocketResponseAs[TT, _], ws: WebSocket[F]): F[TT] =
    r match {
      case ResponseAsWebSocket(f)      => f.asInstanceOf[WebSocket[F] => F[TT]](ws).ensure(ws.close())
      case ResponseAsWebSocketUnsafe() => ws.unit.asInstanceOf[F[TT]]
      case ResponseAsWebSocketStream(_, p) =>
        compileWebSocketPipe(ws, p.asInstanceOf[streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
    }

  def compileWebSocketPipe(ws: WebSocket[F], pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): F[Unit]

  def apply[T](
      responseBody: InputStream,
      responseAs: ResponseAs[T, _],
      responseMetadata: ResponseMetadata,
      ws: Option[WebSocket[F]]
  ): F[T] = {
    (responseAs, ws) match {
      case (raf: ResponseAsFromMetadata[T, _], _) => apply(responseBody, raf(responseMetadata), responseMetadata, ws)
      case (MappedResponseAs(raw, g), _) =>
        monad.map(apply(responseBody, raw, responseMetadata, ws))(t => g(t, responseMetadata))
      case (IgnoreResponse, None) =>
        monad.eval(responseBody.close())
      case (ResponseAsByteArray, None) =>
        monad.fromTry {
          val body = Try(toByteArray(responseBody))
          responseBody.close()
          body
        }
      case (_: ResponseAsStreamUnsafe[_, _], None) =>
        monad.eval(responseBodyToStream(responseBody).asInstanceOf[T])
      case (ResponseAsFile(file), None) =>
        monad.fromTry {
          val body = Try(FileHelpers.saveFile(file.toFile, responseBody))
          responseBody.close()
          body.map(_ => file)
        }
      case (ras @ ResponseAsStream(_, _), None) =>
        ras.f
          .asInstanceOf[streams.BinaryStream => F[T]](responseBodyToStream(responseBody))
          .ensure(monad.eval(responseBody.close()))
      case (wr: WebSocketResponseAs[T, _], Some(_ws)) =>
        fromWs(wr, _ws)
      case (_: WebSocketResponseAs[T, _], None) =>
        responseBody.close()
        monad.error(new NotAWebSocketException(responseMetadata.code))
      case (_, Some(ws)) =>
        ws.close().flatMap(_ => monad.error(new GotAWebSocketException()))
    }
  }
}
