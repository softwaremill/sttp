package sttp.client3.impl.zio

import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}
import zio.stream.ZStream
import zio.{Ref, ZIO}

object ZioWebSockets {
  def compilePipe[R](
      ws: WebSocket[ZIO[R, Throwable, *]],
      pipe: ZStream[R, Throwable, WebSocketFrame.Data[_]] => ZStream[R, Throwable, WebSocketFrame]
  ): ZIO[R, Throwable, Unit] =
    Ref.make(true).flatMap { open =>
      val onClose = ZStream.fromZIO(open.set(false).map(_ => None: Option[WebSocketFrame.Data[_]]))
      pipe(
        ZStream
          .repeatZIO(ws.receive())
          .flatMap {
            case WebSocketFrame.Close(_, _) => onClose
            case WebSocketFrame.Ping(payload) =>
              ZStream.fromZIO(ws.send(WebSocketFrame.Pong(payload))).flatMap(_ => ZStream.empty)
            case WebSocketFrame.Pong(_)     => ZStream.empty
            case in: WebSocketFrame.Data[_] => ZStream(Some(in))
          }
          .catchSome { case _: WebSocketClosed => onClose }
          .collectWhileSome
      )
        .mapZIO(ws.send(_))
        .foreach(_ => ZIO.unit)
        .ensuring(ws.close().catchAll(_ => ZIO.unit))
    }

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
