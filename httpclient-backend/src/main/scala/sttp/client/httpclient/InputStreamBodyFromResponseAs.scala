package sttp.client.httpclient

import java.io.{BufferedInputStream, ByteArrayInputStream, FileInputStream, InputStream}

import sttp.client.internal.{BodyFromResponseAs, FileHelpers, SttpFile}
import sttp.client.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client.{ResponseMetadata, WebSocketResponseAs}
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorValueOps
import sttp.ws.WebSocket

abstract class InputStreamBodyFromResponseAs[F[_], S](implicit
    monad: MonadError[F]
) extends BodyFromResponseAs[F, InputStream, WebSocket[F], S] {
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
    monad.eval {
      try response.readAllBytes()
      finally response.close()
    }

  override protected def regularAsFile(response: InputStream, file: SttpFile): F[SttpFile] =
    monad.eval {
      try {
        FileHelpers.saveFile(file.toFile, response)
        file
      } finally response.close()
    }

  override protected def handleWS[T](
      responseAs: WebSocketResponseAs[T, _],
      meta: ResponseMetadata,
      ws: WebSocket[F]
  ): F[T]

  override protected def cleanupWhenNotAWebSocket(
      response: InputStream,
      e: NotAWebSocketException
  ): F[Unit] = monad.eval(response.close())

  override protected def cleanupWhenGotWebSocket(
      response: WebSocket[F],
      e: GotAWebSocketException
  ): F[Unit] = response.close()
}
