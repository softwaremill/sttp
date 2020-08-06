package sttp.client.okhttp

import java.io.InputStream

import sttp.client.ResponseAs.EagerResponseHandler
import sttp.client.internal.{FileHelpers, toByteArray}
import sttp.client.{
  BasicResponseAs,
  IgnoreResponse,
  ResponseAs,
  ResponseAsByteArray,
  ResponseAsFile,
  ResponseAsStream,
  ResponseAsStreamUnsafe,
  ResponseAsWebSocket,
  ResponseAsWebSocketStream,
  ResponseAsWebSocketUnsafe,
  ResponseMetadata,
  Streams,
  WebSocketResponseAs
}
import sttp.client.monad.{MonadAsyncError, MonadError}
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame
import sttp.client.monad.syntax._

import scala.util.Try

trait BodyFromOkHttp[F[_], S] {
  val streams: Streams[S]
  implicit def monad: MonadError[F]

  def responseBodyToStream(inputStream: InputStream): streams.BinaryStream

  def fromWs[TT](r: WebSocketResponseAs[TT, _], ws: WebSocket[F]): F[TT] =
    r match {
      case ResponseAsWebSocket(f)      => f.asInstanceOf[WebSocket[F] => F[TT]](ws).ensure(ws.close)
      case ResponseAsWebSocketUnsafe() => ws.unit.asInstanceOf[F[TT]]
      case ResponseAsWebSocketStream(_, p) =>
        compileWebSocketPipe(ws, p.asInstanceOf[streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
    }

  def compileWebSocketPipe(ws: WebSocket[F], pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): F[Unit]

  def apply[T](byteBody: InputStream, responseAs: ResponseAs[T, _], responseMetadata: ResponseMetadata): F[T] =
    responseHandler(byteBody).handle(responseAs, monad, responseMetadata)

  private def responseHandler[R](responseBody: InputStream): EagerResponseHandler[R, F] =
    new EagerResponseHandler[R, F] { //TODO do we still need it?
      override def handleStream[T](ras: ResponseAsStream[F, _, _, _]): F[T] = {
        ras.f
          .asInstanceOf[streams.BinaryStream => F[T]](responseBodyToStream(responseBody))
          .ensure(monad.eval(responseBody.close()))
      }

      override def handleBasic[T](bra: BasicResponseAs[T, R]): Try[T] =
        bra match {
          case IgnoreResponse =>
            Try(responseBody.close())
          case ResponseAsByteArray =>
            val body = Try(toByteArray(responseBody))
            responseBody.close()
            body
          case _: ResponseAsStreamUnsafe[_, _] =>
            responseBodyToStream(responseBody).asInstanceOf[Try[T]]
          case ResponseAsFile(file) =>
            val body = Try(FileHelpers.saveFile(file.toFile, responseBody))
            responseBody.close()
            body.map(_ => file)
        }
    }
}
