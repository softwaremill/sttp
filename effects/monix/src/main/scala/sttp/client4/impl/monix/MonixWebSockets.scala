package sttp.client4.impl.monix

import cats.effect.concurrent.Ref
import monix.eval.Task
import monix.execution.cancelables.BooleanCancelable
import monix.reactive.Observable
import sttp.capabilities.monix.MonixStreams
import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}

object MonixWebSockets {
  def compilePipe(
      ws: WebSocket[Task],
      pipe: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
  ): Task[Unit] =
    Task(BooleanCancelable()).flatMap { wsClosed =>
      Ref.of[Task, Option[WebSocketFrame.Close]](None).flatMap { closeRef =>
        Ref.of[Task, Boolean](false).flatMap { closeSent =>
          // set the close to echo (a received Close) or none (the connection is already gone),
          // then terminate the stream
          def onClose(close: Option[WebSocketFrame.Close]): Task[Option[WebSocketFrame.Data[_]]] =
            closeRef.set(close) >> Task {
              wsClosed.cancel()
              None
            }
          pipe(
            Observable
              .repeatEvalF(ws.receive().flatMap[Option[WebSocketFrame.Data[_]]] {
                case WebSocketFrame.Close(code, reason) => onClose(Some(WebSocketFrame.Close(code, reason)))
                case WebSocketFrame.Ping(payload)       => ws.send(WebSocketFrame.Pong(payload)).map(_ => None)
                case WebSocketFrame.Pong(_)             => Task.now(None)
                case in: WebSocketFrame.Data[_]         => Task.now(Some(in))
              })
              .onErrorRecoverWith { case _: WebSocketClosed => Observable.from(onClose(None)) }
              .takeWhileNotCanceled(wsClosed)
              .flatMap {
                case None    => Observable.empty
                case Some(f) => Observable(f)
              }
          )
            // end with matching Close or user-provided Close or no Close at all
            .++(Observable.fromTask(closeRef.get).flatMap {
              case Some(close) => Observable(close: WebSocketFrame)
              case None        => Observable.empty
            })
            // a data frame following a non-final fragment is a continuation; a Close never is
            .mapAccumulate(false) {
              case (afterNonFinal, frame: WebSocketFrame.Data[_]) => (!frame.finalFragment, (frame, afterNonFinal))
              case (afterNonFinal, frame)                         => (afterNonFinal, (frame, false))
            }
            .mapEval { case (frame, isContinuation) =>
              ws.send(frame, isContinuation) >> (frame match {
                case _: WebSocketFrame.Close => closeSent.set(true)
                case _                       => Task.unit
              })
            }
            .completedL
            // the stream already emits a Close when appropriate; only fall back to a default Close if none was sent
            .guarantee(closeSent.get.flatMap(sent => if (sent) Task.unit else ws.close()))
        }
      }
    }
  def fromTextPipe: (String => WebSocketFrame) => MonixStreams.Pipe[WebSocketFrame, WebSocketFrame] =
    f => fromTextPipeF(_.map(f))

  def fromTextPipeF: MonixStreams.Pipe[String, WebSocketFrame] => MonixStreams.Pipe[WebSocketFrame, WebSocketFrame] =
    p => p.compose(combinedTextFrames)

  def combinedTextFrames: MonixStreams.Pipe[WebSocketFrame, String] = { input =>
    input
      .collect { case tf: WebSocketFrame.Text => tf }
      .bufferWhileInclusive(!_.finalFragment)
      .map(frames => frames.map(_.payload).mkString)
  }
}
