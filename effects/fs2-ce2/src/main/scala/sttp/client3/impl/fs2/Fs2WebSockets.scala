package sttp.client3.impl.fs2

import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Ref
import cats.effect.implicits._
import fs2.{Pipe, Stream}
import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}

object Fs2WebSockets {

  /** Handle the websocket through a [[Pipe]] which receives the incoming events and produces the messages to be sent
    * to the server. Not that by the nature of a [[Pipe]], there no need that these two streams are coupled. Just make sure
    * to consume the input as otherwise the receiving buffer might overflow (use [[Stream.drain]] if you want to discard).
    * @param ws the websocket to handle
    * @param pipe the pipe to handle the socket
    * @tparam F the effect type
    * @return an Unit effect describing the full run of the websocket through the pipe
    */
  def handleThroughPipe[F[_]: ConcurrentEffect](
      ws: WebSocket[F]
  )(pipe: Pipe[F, WebSocketFrame.Data[_], WebSocketFrame]): F[Unit] = {
    Stream
      .eval(Ref.of[F, Option[WebSocketFrame.Close]](None))
      .flatMap { closeRef =>
        Stream
          .repeatEval(ws.receive()) // read incoming messages
          .flatMap[F, Option[WebSocketFrame.Data[_]]] {
            case WebSocketFrame.Close(code, reason) =>
              Stream.eval(closeRef.set(Some(WebSocketFrame.Close(code, reason)))).as(None)
            case WebSocketFrame.Ping(payload) =>
              Stream.eval(ws.send(WebSocketFrame.Pong(payload))).drain
            case WebSocketFrame.Pong(_) =>
              Stream.empty // ignore
            case in: WebSocketFrame.Data[_] => Stream.emit(Some(in))
          }
          .handleErrorWith {
            case _: WebSocketClosed => Stream.eval(closeRef.set(None)).as(None)
            case e                  => Stream.eval(ConcurrentEffect[F].raiseError(e))
          }
          .unNoneTerminate // terminate once we got a Close
          .through(pipe)
          // end with matching Close or user-provided Close or no Close at all
          .append(Stream.eval(closeRef.get).unNone) // A Close isn't a continuation
          .evalMap(ws.send(_)) // send messages
      }
      .compile
      .drain
      .guarantee(ws.close())
  }

  def fromTextPipe[F[_]]: (String => WebSocketFrame) => fs2.Pipe[F, WebSocketFrame, WebSocketFrame] =
    f => fromTextPipeF(_.map(f))

  def fromTextPipeF[F[_]]: fs2.Pipe[F, String, WebSocketFrame] => fs2.Pipe[F, WebSocketFrame, WebSocketFrame] =
    p => p.compose(combinedTextFrames)

  def combinedTextFrames[F[_]]: fs2.Pipe[F, WebSocketFrame, String] = { input =>
    input
      .collect { case tf: WebSocketFrame.Text => tf }
      .flatMap { tf =>
        if (tf.finalFragment) {
          Stream(tf.copy(finalFragment = false), tf.copy(payload = ""))
        } else {
          Stream(tf)
        }
      }
      .split(_.finalFragment)
      .map(chunks => chunks.map(_.payload).toList.mkString)
  }
}
