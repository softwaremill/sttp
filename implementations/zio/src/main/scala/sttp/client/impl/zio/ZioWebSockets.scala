package sttp.client.impl.zio

import sttp.ws.{WebSocket, WebSocketFrame}
import zio.{Ref, ZIO}
import zio.stream.{Stream, ZTransducer}

object ZioWebSockets {
  def compilePipe[R](
      ws: WebSocket[ZIO[R, Throwable, *]],
      pipe: ZTransducer[R, Throwable, WebSocketFrame.Data[_], WebSocketFrame]
  ): ZIO[R, Throwable, Unit] =
    Ref.make(true).flatMap { open =>
      Stream
        .repeatEffect(ws.receive())
        .flatMap {
          case WebSocketFrame.Close(_, _)   => Stream.fromEffect(open.set(false))
          case WebSocketFrame.Ping(payload) => Stream.fromEffect(ws.send(WebSocketFrame.Pong(payload)))
          case WebSocketFrame.Pong(_)       => Stream.empty
          case in: WebSocketFrame.Data[_]   => Stream(in).transduce(pipe).mapM(ws.send(_))
        }
        .foreachWhile(_ => open.get)
        .ensuring(ws.close().catchAll(_ => ZIO.unit))
    }
}
