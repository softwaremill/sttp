package sttp.client.impl.zio

import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame
import zio.{Ref, ZIO}
import zio.stream.{Stream, Transducer}

object ZioWebSockets {
  def compilePipe[R](
      ws: WebSocket[ZIO[R, Throwable, *]],
      pipe: Transducer[Throwable, WebSocketFrame.Data[_], WebSocketFrame]
  ): ZIO[R, Throwable, Unit] =
    Ref.make(false).flatMap { closed =>
      Stream
        .repeatEffect(ws.receive)
        .flatMap {
          case Left(WebSocketFrame.Close(_, _))    => Stream.fromEffect(closed.set(true))
          case Right(WebSocketFrame.Ping(payload)) => Stream.fromEffect(ws.send(WebSocketFrame.Pong(payload)))
          case Right(WebSocketFrame.Pong(_))       => Stream.empty
          case Right(in: WebSocketFrame.Data[_])   => Stream(in).transduce(pipe).mapM(ws.send(_))
        }
        .foreachWhile(_ => closed.get)
        .ensuring(ws.close.catchAll(_ => ZIO.unit))
    }
}
