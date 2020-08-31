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
}
