package sttp.client4.internal.httpclient

import sttp.client4.GenericWebSocketResponseAs
import sttp.client4.internal.{BodyFromResponseAs, FileHelpers, SttpFile}
import sttp.client4.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.model.ResponseMetadata
import sttp.monad.syntax.MonadErrorValueOps
import sttp.ws.WebSocket

import java.io.{BufferedInputStream, ByteArrayInputStream, FileInputStream, InputStream}

private[client4] trait InputStreamBodyFromHttpClient[F[_], S] extends BodyFromHttpClient[F, S, InputStream] {

  override protected lazy val bodyFromResponseAs =
    new BodyFromResponseAs[F, InputStream, WebSocket[F], streams.BinaryStream]() {
      override protected def withReplayableBody(
          response: InputStream,
          replayableBody: Either[Array[Byte], SttpFile]
      ): F[InputStream] =
        (replayableBody match {
          case Left(byteArray) => new ByteArrayInputStream(byteArray)
          case Right(file)     => new BufferedInputStream(new FileInputStream(file.toFile))
        }).unit

      override protected def regularIgnore(response: InputStream): F[Unit] = monad.blocking(response.close())

      override protected def regularAsByteArray(response: InputStream): F[Array[Byte]] =
        monad.blocking {
          try response.readAllBytes()
          finally response.close()
        }

      override protected def regularAsFile(response: InputStream, file: SttpFile): F[SttpFile] =
        monad.blocking {
          try {
            FileHelpers.saveFile(file.toFile, response)
            file
          } finally response.close()
        }

      override protected def regularAsStream(response: InputStream): F[(streams.BinaryStream, () => F[Unit])] =
        inputStreamToStream(response)

      override protected def regularAsInputStream(response: InputStream): F[InputStream] = monad.unit(response)

      override protected def handleWS[T](
          responseAs: GenericWebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: WebSocket[F]
      ): F[T] = bodyFromWs(responseAs, ws, meta)

      override protected def cleanupWhenNotAWebSocket(response: InputStream, e: NotAWebSocketException): F[Unit] =
        monad.blocking(response.close())

      override protected def cleanupWhenGotWebSocket(response: WebSocket[F], e: GotAWebSocketException): F[Unit] =
        response.close()
    }

  def inputStreamToStream(is: InputStream): F[(streams.BinaryStream, () => F[Unit])]
}
