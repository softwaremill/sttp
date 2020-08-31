package sttp.client.impl.zio

import sttp.ws.{WebSocket, WebSocketFrame}
import zio.{Ref, ZIO}
import zio.stream.{Stream, ZStream, ZTransducer}

object ZioWebSockets {
  def compilePipe[R](
      ws: WebSocket[ZIO[R, Throwable, *]],
      pipe: ZStream[R, Throwable, WebSocketFrame.Data[_]] => ZStream[R, Throwable, WebSocketFrame]
  ): ZIO[R, Throwable, Unit] =
    Ref.make(true).flatMap { open =>
      pipe(
        Stream
          .repeatEffect(ws.receive())
          .flatMap {
            case WebSocketFrame.Close(_, _) =>
              Stream.fromEffect(open.set(false).map(_ => None: Option[WebSocketFrame.Data[_]]))
            case WebSocketFrame.Ping(payload) =>
              Stream.fromEffect(ws.send(WebSocketFrame.Pong(payload))).flatMap(_ => Stream.empty)
            case WebSocketFrame.Pong(_)     => Stream.empty
            case in: WebSocketFrame.Data[_] => Stream(Some(in))
          }
          .collectWhileSome
      )
        .mapM(ws.send(_))
        .foreach(_ => ZIO.unit)
        .ensuring(ws.close().catchAll(_ => ZIO.unit))
    }
}
