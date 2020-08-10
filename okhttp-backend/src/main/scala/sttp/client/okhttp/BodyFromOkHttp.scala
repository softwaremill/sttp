package sttp.client.okhttp

import java.io.InputStream

import sttp.client.internal.{FileHelpers, toByteArray}
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.ws.WebSocket
import sttp.client.{
  BasicResponseAs,
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
import sttp.model.ws.WebSocketFrame

import scala.util.Try

trait BodyFromOkHttp[F[_], S] {
  val streams: Streams[S]
  implicit def monad: MonadError[F]

  def responseBodyToStream(inputStream: InputStream): streams.BinaryStream

  private def fromWs[TT](r: WebSocketResponseAs[TT, _], ws: WebSocket[F]): F[TT] =
    r match {
      case ResponseAsWebSocket(f)      => f.asInstanceOf[WebSocket[F] => F[TT]](ws).ensure(ws.close)
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
    responseAs match {
      case bra: BasicResponseAs[T, _] => handleBasic(responseBody, bra)
      case wr: WebSocketResponseAs[T, _] =>
        ws match {
          case Some(value) => fromWs(wr, value)
          case None        => throw new IllegalStateException("something something")
        }
      case ras @ ResponseAsStream(s, f) =>
        ras.f
          .asInstanceOf[streams.BinaryStream => F[T]](responseBodyToStream(responseBody))
          .ensure(monad.eval(responseBody.close()))
      case ResponseAsFromMetadata(f) => apply(responseBody, f(responseMetadata), responseMetadata, ws)
      case MappedResponseAs(raw, g) =>
        monad.map(apply(responseBody, raw, responseMetadata, ws))(t => g(t, responseMetadata))
    }
  }

  private def handleBasic[T](responseBody: InputStream, bra: BasicResponseAs[T, _]): F[T] = {
    monad.fromTry(bra match {
      case IgnoreResponse =>
        Try(responseBody.close())
      case ResponseAsByteArray =>
        val body = Try(toByteArray(responseBody))
        responseBody.close()
        body
      case _: ResponseAsStreamUnsafe[_, _] =>
        Try(responseBodyToStream(responseBody).asInstanceOf[T])
      case ResponseAsFile(file) =>
        val body = Try(FileHelpers.saveFile(file.toFile, responseBody))
        responseBody.close()
        body.map(_ => file)
    })
  }
}
