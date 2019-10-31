package sttp.client.impl.monix

import monix.eval.Task
import sttp.client.ws.WebSocketResponse
import sttp.client.{Request, Response, SttpBackend}

import scala.language.higherKinds

trait ShiftToDefaultScheduler[F[_], S, WS_HANDLER[_]] extends SttpBackend[Task, S, WS_HANDLER] {

  override abstract def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WS_HANDLER[WS_RESULT]
  ): Task[WebSocketResponse[WS_RESULT]] = {
    super.openWebsocket(request, handler).guarantee(Task.shift)
  }

  override abstract def send[T](request: Request[T, S]): Task[Response[T]] = {
    super.send(request).guarantee(Task.shift)
  }

}
