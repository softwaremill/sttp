package sttp.client3.httpclient

import java.io.{BufferedInputStream, ByteArrayInputStream, FileInputStream, InputStream}
import sttp.client3.internal.{BodyFromResponseAs, FileHelpers, SttpFile}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client3.WebSocketResponseAs
import sttp.model.ResponseMetadata
import sttp.monad.syntax.MonadErrorValueOps
import sttp.ws.WebSocket

trait InputStreamBodyFromHttpClient[F[_], S] extends BodyFromHttpClient[F, S, InputStream] {

  override protected lazy val bodyFromResponseAs =
    new BodyFromResponseAs[F, InputStream, WebSocket[F], streams.BinaryStream]() {
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

      override protected def regularAsStream(response: InputStream): F[(streams.BinaryStream, () => F[Unit])] =
        inputStreamToStream(response)

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: WebSocket[F]
      ): F[T] = bodyFromWs(responseAs, ws, meta)

      override protected def cleanupWhenNotAWebSocket(response: InputStream, e: NotAWebSocketException): F[Unit] =
        monad.eval(response.close())

      override protected def cleanupWhenGotWebSocket(response: WebSocket[F], e: GotAWebSocketException): F[Unit] =
        response.close()
    }

  def inputStreamToStream(is: InputStream): F[(streams.BinaryStream, () => F[Unit])]
}
