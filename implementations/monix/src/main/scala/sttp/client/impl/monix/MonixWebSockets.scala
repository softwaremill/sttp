package sttp.client.impl.monix

import monix.eval.Task
import monix.execution.cancelables.BooleanCancelable
import monix.reactive.Observable
import sttp.ws.{WebSocket, WebSocketFrame}

object MonixWebSockets {
  def compilePipe(
      ws: WebSocket[Task],
      pipe: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
  ): Task[Unit] = {
    Task(BooleanCancelable()).flatMap { wsClosed =>
      Observable
        .repeatEvalF(ws.receive)
        .flatMap {
          case WebSocketFrame.Close(_, _)   => Observable.fromTask(Task(wsClosed.cancel()))
          case WebSocketFrame.Ping(payload) => Observable.fromTask(ws.send(WebSocketFrame.Pong(payload)))
          case WebSocketFrame.Pong(_)       => Observable.empty
          case in: WebSocketFrame.Data[_]   => pipe(Observable(in)).mapEval(ws.send(_))
        }
        .takeWhileNotCanceled(wsClosed)
        .completedL
        .guarantee(ws.close)
    }
  }
}
