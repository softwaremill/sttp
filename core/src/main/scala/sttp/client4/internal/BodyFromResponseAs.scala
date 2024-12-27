package sttp.client4.internal

import sttp.client4._
import sttp.client4.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.model.ResponseMetadata
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.InputStream

abstract class BodyFromResponseAs[F[_], RegularResponse, WSResponse, Stream](implicit m: MonadError[F]) {
  def apply[T](
      responseAs: ResponseAsDelegate[T, _],
      meta: ResponseMetadata,
      response: Either[RegularResponse, WSResponse]
  ): F[T] = doApply(responseAs.delegate, meta, response).map(_._1)

  private def doApply[T](
      responseAs: GenericResponseAs[T, _],
      meta: ResponseMetadata,
      response: Either[RegularResponse, WSResponse]
  ): F[(T, ReplayableBody)] =
    (responseAs, response) match {
      case (MappedResponseAs(raw, g, _), _) =>
        doApply(raw, meta, response).flatMap { case (result, replayableBody) =>
          m.eval(g(result, meta)).map((_, replayableBody))
        }

      case (rfm: ResponseAsFromMetadata[T, _] @unchecked, _) => doApply(rfm(meta), meta, response)

      case (ResponseAsBoth(l, r), _) =>
        doApply(l, meta, response).flatMap {
          case (leftResult, None) => ((leftResult, None): T, nonReplayableBody).unit
          case (leftResult, Some(rb)) =>
            (response match {
              case Left(rr)  => withReplayableBody(rr, rb).map(Left(_))
              case Right(ws) => Right(ws).unit
            }).flatMap { replayableResponse =>
              doApply(r, meta, replayableResponse).map { case (rightResult, _) =>
                ((leftResult, Some(rightResult)), Some(rb))
              }
            }
        }

      case (IgnoreResponse, Left(regular)) =>
        regularIgnore(regular).map(_ => ((), nonReplayableBody))

      case (ResponseAsByteArray, Left(regular)) =>
        regularAsByteArray(regular).map(b => (b, replayableBody(b)))

      case (ResponseAsStream(_, f), Left(regular)) =>
        regularAsStream(regular).flatMap { case (stream, cancel) =>
          m.suspend(f.asInstanceOf[(Stream, ResponseMetadata) => F[T]](stream, meta))
            .map((_, nonReplayableBody))
            .ensure(cancel())
        }

      case (ResponseAsStreamUnsafe(_), Left(regular)) =>
        regularAsStream(regular).map { case (stream, _) =>
          (stream.asInstanceOf[T], nonReplayableBody)
        }

      case (ResponseAsInputStream(f), Left(regular)) =>
        regularAsInputStream(regular)
          .flatMap(w => m.eval(f(w)).ensure(m.eval(w.close())))
          .map(t => (t, nonReplayableBody))
      case (ResponseAsInputStreamUnsafe, Left(regular)) =>
        regularAsInputStream(regular).map(w => (w, nonReplayableBody))

      case (ResponseAsFile(file), Left(regular)) =>
        regularAsFile(regular, file).map(f => (f, replayableBody(f)))

      case (wsr: GenericWebSocketResponseAs[_, _], Right(ws)) =>
        handleWS(wsr.asInstanceOf[GenericWebSocketResponseAs[T, _]], meta, ws)
          .map(w => (w, nonReplayableBody))

      case (_: GenericWebSocketResponseAs[_, _], Left(regular)) =>
        val e = new NotAWebSocketException(meta.code)
        cleanupWhenNotAWebSocket(regular, e).flatMap(_ => m.error(e))

      case (_, Right(ws)) =>
        val e = new GotAWebSocketException()
        cleanupWhenGotWebSocket(ws, e).flatMap(_ => m.error(e))
    }

  protected def withReplayableBody(
      response: RegularResponse,
      replayableBody: Either[Array[Byte], SttpFile]
  ): F[RegularResponse]
  protected def regularIgnore(response: RegularResponse): F[Unit]
  protected def regularAsByteArray(response: RegularResponse): F[Array[Byte]]
  protected def regularAsFile(response: RegularResponse, file: SttpFile): F[SttpFile]
  protected def regularAsStream(response: RegularResponse): F[(Stream, () => F[Unit])]
  protected def regularAsInputStream(response: RegularResponse): F[InputStream] =
    throw new UnsupportedOperationException("Responses as a java.io.InputStream are not supported")
  protected def handleWS[T](responseAs: GenericWebSocketResponseAs[T, _], meta: ResponseMetadata, ws: WSResponse): F[T]
  protected def cleanupWhenNotAWebSocket(response: RegularResponse, e: NotAWebSocketException): F[Unit]
  protected def cleanupWhenGotWebSocket(response: WSResponse, e: GotAWebSocketException): F[Unit]
}
