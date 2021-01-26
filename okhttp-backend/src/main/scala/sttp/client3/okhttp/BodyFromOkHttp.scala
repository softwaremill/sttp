package sttp.client3.okhttp

import java.io.{BufferedInputStream, ByteArrayInputStream, FileInputStream, InputStream}
import sttp.capabilities.Streams
import sttp.client3.internal.{BodyFromResponseAs, FileHelpers, SttpFile, toByteArray}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client3.{
  ResponseAs,
  ResponseAsWebSocket,
  ResponseAsWebSocketStream,
  ResponseAsWebSocketUnsafe,
  WebSocketResponseAs
}
import sttp.model.ResponseMetadata
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.util.Try

private[okhttp] trait BodyFromOkHttp[F[_], S] {
  val streams: Streams[S]
  implicit val monad: MonadError[F]

  def responseBodyToStream(inputStream: InputStream): streams.BinaryStream

  private def fromWs[TT](r: WebSocketResponseAs[TT, _], ws: WebSocket[F], meta: ResponseMetadata): F[TT] =
    r match {
      case ResponseAsWebSocket(f) =>
        f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[TT]](ws, meta).ensure(ws.close())
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
  ): F[T] = bodyFromResponseAs(responseAs, responseMetadata, ws.toRight(responseBody))

  private lazy val bodyFromResponseAs =
    new BodyFromResponseAs[F, InputStream, WebSocket[F], streams.BinaryStream] {
      override protected def withReplayableBody(
          response: InputStream,
          replayableBody: Either[Array[Byte], SttpFile]
      ): F[InputStream] = {
        (replayableBody match {
          case Left(byteArray) => new ByteArrayInputStream(byteArray)
          case Right(file)     => new BufferedInputStream(new FileInputStream(file.toFile))
        }).unit
      }

      override protected def regularIgnore(response: InputStream): F[Unit] = monad.eval(response.close())

      override protected def regularAsByteArray(response: InputStream): F[Array[Byte]] =
        monad.fromTry {
          val body = Try(toByteArray(response))
          response.close()
          body
        }

      override protected def regularAsFile(response: InputStream, file: SttpFile): F[SttpFile] =
        monad
          .fromTry {
            val body = Try(FileHelpers.saveFile(file.toFile, response))
            response.close()
            body.map(_ => file)
          }

      override protected def regularAsStream(response: InputStream): F[(streams.BinaryStream, () => F[Unit])] =
        monad.eval((responseBodyToStream(response), () => monad.eval(response.close())))

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: WebSocket[F]
      ): F[T] = fromWs(responseAs, ws, meta)

      override protected def cleanupWhenNotAWebSocket(response: InputStream, e: NotAWebSocketException): F[Unit] =
        monad.eval(response.close())

      override protected def cleanupWhenGotWebSocket(response: WebSocket[F], e: GotAWebSocketException): F[Unit] =
        response.close()
    }
}
