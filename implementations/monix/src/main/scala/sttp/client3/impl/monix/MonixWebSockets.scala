package sttp.client3.impl.monix

import monix.eval.Task
import monix.execution.cancelables.BooleanCancelable
import monix.reactive.Observable
import sttp.capabilities.monix.MonixStreams
import sttp.ws.{WebSocket, WebSocketFrame}

object MonixWebSockets {
  def compilePipe(
      ws: WebSocket[Task],
      pipe: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
  ): Task[Unit] = {
    Task(BooleanCancelable()).flatMap { wsClosed =>
      pipe(
        Observable
          .repeatEvalF(ws.receive().flatMap[Option[WebSocketFrame.Data[_]]] {
            case WebSocketFrame.Close(_, _)   => Task(wsClosed.cancel()).map(_ => None)
            case WebSocketFrame.Ping(payload) => ws.send(WebSocketFrame.Pong(payload)).map(_ => None)
            case WebSocketFrame.Pong(_)       => Task.now(None)
            case in: WebSocketFrame.Data[_]   => Task.now(Some(in))
          })
          .takeWhileNotCanceled(wsClosed)
          .flatMap {
            case None    => Observable.empty
            case Some(f) => Observable(f)
          }
      )
        .mapEval(ws.send(_))
        .completedL
        .guarantee(ws.close())
    }
  }
  def fromTextPipe: (String => WebSocketFrame) => MonixStreams.Pipe[WebSocketFrame, WebSocketFrame] =
    f => fromTextPipeF(_.map(f))

  def fromTextPipeF: MonixStreams.Pipe[String, WebSocketFrame] => MonixStreams.Pipe[WebSocketFrame, WebSocketFrame] =
    p => p.compose(combinedTextFrames)

  def combinedTextFrames: MonixStreams.Pipe[WebSocketFrame, String] = { input =>
    input
      .collect { case tf: WebSocketFrame.Text => tf }
      .flatMap { tf =>
        if (tf.finalFragment) {
          Observable(tf.copy(finalFragment = false), tf.copy(payload = ""))
        } else {
          Observable(tf)
        }
      }
      .mapAccumulate(List[WebSocketFrame.Text]()) {
        case (acc, tf) if tf.finalFragment => (Nil, acc.reverse)
        case (acc, tf)                     => (tf :: acc, Nil)
      }
      .concatMapIterable {
        case Nil => Nil
        case l   => List(l.map(_.payload).mkString)
      }
  }
}
