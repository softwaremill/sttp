package sttp.client.httpclient

import java.{util => ju}
import java.io.{BufferedInputStream, ByteArrayInputStream, FileInputStream, InputStream}
import java.nio.ByteBuffer
import java.util.concurrent.Flow

import org.reactivestreams.Publisher
import sttp.capabilities.Streams
import sttp.client.internal.{BodyFromResponseAs, FileHelpers, SttpFile}
import sttp.client.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client.{ResponseAs, ResponseAsWebSocket, ResponseAsWebSocketStream, ResponseAsWebSocketUnsafe, ResponseMetadata, WebSocketResponseAs}
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
  ): F[T] //= bodyFromResponseAs(responseAs, responseMetadata, response)

//  private lazy val bodyFromResponseAs =
//    new BodyFromResponseAs[F, InputStream, WebSocket[F], streams.BinaryStream] {
//      override protected def withReplayableBody(
//          response: InputStream,
//          replayableBody: Either[Array[Byte], SttpFile]
//      ): F[B] = {
//        (replayableBody match {
//          case Left(byteArray) => new ByteArrayInputStream(byteArray)
//          case Right(file)     => new BufferedInputStream(new FileInputStream(file.toFile))
//        }).unit
//      }
//
//      override protected def regularIgnore(response: InputStream): F[Unit] = monad.eval(response.close())
//
//      override protected def regularAsByteArray(response: B): F[Array[Byte]] =
//        monad.eval {
//          try response.readAllBytes()
//          finally response.close()
//        }
//
//      override protected def regularAsFile(response: B, file: SttpFile): F[SttpFile] =
//        monad.eval {
//          try {
//            FileHelpers.saveFile(file.toFile, response)
//            file
//          } finally response.close()
//        }
//
//      override protected def regularAsStream(response: B): F[(streams.BinaryStream, () => F[Unit])] =
//        monad.eval((inputStreamToStream(response), () => monad.eval(response.close())))
//
//      override protected def handleWS[T](
//          responseAs: WebSocketResponseAs[T, _],
//          meta: ResponseMetadata,
//          ws: WebSocket[F]
//      ): F[T] = bodyFromWs(responseAs, ws)
//
//      override protected def cleanupWhenNotAWebSocket(response: InputStream, e: NotAWebSocketException): F[Unit] =
//        monad.eval(response.close())
//
//      override protected def cleanupWhenGotWebSocket(response: WebSocket[F], e: GotAWebSocketException): F[Unit] =
//        response.close()
//    }

  protected def bodyFromWs[T](r: WebSocketResponseAs[T, _], ws: WebSocket[F]): F[T] =
    r match {
      case ResponseAsWebSocket(f)      => f.asInstanceOf[WebSocket[F] => F[T]](ws).ensure(ws.close())
      case ResponseAsWebSocketUnsafe() => ws.unit.asInstanceOf[F[T]]
      case ResponseAsWebSocketStream(_, p) =>
        compileWebSocketPipe(ws, p.asInstanceOf[streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
    }
}
