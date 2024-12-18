package sttp.client4.asynchttpclient

import org.asynchttpclient.{Response => AHCResponse}
import sttp.client4._
import sttp.client4.internal._
import sttp.client4.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.model.ResponseMetadata
import sttp.monad.MonadAsyncError
import sttp.monad.syntax._
import sttp.ws.WebSocket

private[asynchttpclient] class BodyFromAHC[F[_]](implicit monad: MonadAsyncError[F]) {
  private def bodyFromResponseAs =
    new BodyFromResponseAs[F, AHCResponse, WebSocket[F], Nothing] {
      override protected def withReplayableBody(
          response: AHCResponse,
          replayableBody: Either[Array[Byte], SttpFile]
      ): F[AHCResponse] =
        replayableBody match {
          case Left(byteArray) => monad.unit(response) // TODO
          case Right(file)     => monad.unit(response) // TODO
        }

      override protected def regularIgnore(response: AHCResponse): F[Unit] =
        // getting the body and discarding it
        monad.eval {
          discard(response)
          ((), nonReplayableBody)
        }

      override protected def regularAsByteArray(response: AHCResponse): F[Array[Byte]] =
        monad.eval(response.getResponseBodyAsBytes)

      override protected def regularAsFile(response: AHCResponse, file: SttpFile): F[SttpFile] =
        monad.eval(FileHelpers.saveFile(file.toFile, response.getResponseBodyAsStream)).map(_ => file)

      override protected def regularAsStream(response: AHCResponse): F[(Nothing, () => F[Unit])] =
        monad.error(new IllegalStateException("Streaming isn't supported"))

      override protected def handleWS[T](
          responseAs: GenericWebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: WebSocket[F]
      ): F[T] = bodyFromWs(responseAs, ws, meta)

      override protected def cleanupWhenNotAWebSocket(response: AHCResponse, e: NotAWebSocketException): F[Unit] =
        monad.eval(discard(response))

      override protected def cleanupWhenGotWebSocket(ws: WebSocket[F], e: GotAWebSocketException): F[Unit] =
        ws.close()
    }

  def apply[TT](
      response: Either[AHCResponse, WebSocket[F]],
      responseAs: ResponseAsDelegate[TT, _],
      responseMetadata: ResponseMetadata
  ): F[TT] = bodyFromResponseAs(responseAs, responseMetadata, response)

  private def bodyFromWs[TT](r: GenericWebSocketResponseAs[TT, _], ws: WebSocket[F], meta: ResponseMetadata): F[TT] =
    r match {
      case ResponseAsWebSocket(f) =>
        f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[TT]].apply(ws, meta).ensure(ws.close())
      case ResponseAsWebSocketUnsafe()     => ws.unit.asInstanceOf[F[TT]]
      case ResponseAsWebSocketStream(_, _) => monad.error(new IllegalStateException("Streaming isn't supported"))
    }

  private def discard(response: AHCResponse): Unit = {
    val is = response.getResponseBodyAsStream
    val buf = new Array[Byte](4096)
    while (is.read(buf) != -1) {}
  }
}
