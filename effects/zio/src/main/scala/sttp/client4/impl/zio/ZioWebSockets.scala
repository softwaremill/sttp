package sttp.client4.impl.zio

import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}
import zio.stream.ZStream
import zio.{Ref, ZIO}

object ZioWebSockets {
  def compilePipe[R](
      ws: WebSocket[ZIO[R, Throwable, *]],
      pipe: ZStream[R, Throwable, WebSocketFrame.Data[_]] => ZStream[R, Throwable, WebSocketFrame]
  ): ZIO[R, Throwable, Unit] =
    for {
      closeRef <- Ref.make(Option.empty[WebSocketFrame.Close])
      closeSent <- Ref.make(false)
      // set the close to echo (a received Close) or none (the connection is already gone), then terminate the stream
      onClose = (close: Option[WebSocketFrame.Close]) =>
        ZStream.fromZIO(closeRef.set(close).as(Option.empty[WebSocketFrame.Data[_]]))
      _ <-
        pipe(
          ZStream
            .repeatZIO(ws.receive())
            .flatMap {
              case WebSocketFrame.Close(code, reason) => onClose(Some(WebSocketFrame.Close(code, reason)))
              case WebSocketFrame.Ping(payload)       =>
                ZStream.fromZIO(ws.send(WebSocketFrame.Pong(payload))).flatMap(_ => ZStream.empty)
              case WebSocketFrame.Pong(_)     => ZStream.empty
              case in: WebSocketFrame.Data[_] => ZStream(Some(in))
            }
            .catchSome { case _: WebSocketClosed => onClose(None) }
            .collectWhileSome
        )
          // end with matching Close or user-provided Close or no Close at all
          .concat(ZStream.fromZIO(closeRef.get).collect { case Some(close) => close })
          // a data frame following a non-final fragment is a continuation; a Close never is
          .mapAccum(false) {
            case (afterNonFinal, frame: WebSocketFrame.Data[_]) => (!frame.finalFragment, (frame, afterNonFinal))
            case (afterNonFinal, frame)                         => (afterNonFinal, (frame, false))
          }
          .mapZIO { case (frame, isContinuation) =>
            ws.send(frame, isContinuation) *> (frame match {
              case _: WebSocketFrame.Close => closeSent.set(true)
              case _                       => ZIO.unit
            })
          }
          .runDrain
          // the stream already emits a Close when appropriate; only fall back to a default Close if none was sent
          .ensuring(closeSent.get.flatMap(sent => if (sent) ZIO.unit else ws.close().catchAll(_ => ZIO.unit)))
    } yield ()

  type PipeR[R, A, B] = ZStream[R, Throwable, A] => ZStream[R, Throwable, B]

  def fromTextPipe[R]: (String => WebSocketFrame) => PipeR[R, WebSocketFrame, WebSocketFrame] =
    f => fromTextPipeF(_.map(f))

  def fromTextPipeF[R]: PipeR[R, String, WebSocketFrame] => PipeR[R, WebSocketFrame, WebSocketFrame] =
    p => p.compose(combinedTextFrames)

  def combinedTextFrames[R]: PipeR[R, WebSocketFrame, String] = { input =>
    input
      .collect { case tf: WebSocketFrame.Text => tf }
      .mapConcat { tf =>
        if (tf.finalFragment) {
          Seq(tf.copy(finalFragment = false), tf.copy(payload = ""))
        } else {
          Seq(tf)
        }
      }
      .split(_.finalFragment)
      .map(_.map(_.payload).mkString)
  }
}
