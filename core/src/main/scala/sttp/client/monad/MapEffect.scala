package sttp.client.monad

import sttp.client.{
  ConditionalResponseAs,
  Effect,
  IgnoreResponse,
  MappedResponseAs,
  RequestBody,
  RequestT,
  ResponseAs,
  ResponseAsByteArray,
  ResponseAsFile,
  ResponseAsFromMetadata,
  ResponseAsStream,
  ResponseAsStreamUnsafe,
  ResponseAsWebSocket,
  ResponseAsWebSocketStream,
  ResponseAsWebSocketUnsafe
}
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame

object MapEffect {

  /**
    * Change the effect type that's used by the response specification of this request, if the response specification
    * requires the `Effect[F]` capability.
    * @param fk A transformation between effects `F` and `G`
    * @tparam F The source effect type.
    * @tparam G The target effect type.
    * @tparam R0 The requirements of this request, without the `Effect[F]` capability.
    */
  def apply[F[_], G[_], U[_], T, R0](
      r: RequestT[U, T, R0 with Effect[F]],
      fk: FunctionK[F, G],
      gk: FunctionK[G, F],
      fm: MonadError[F],
      gm: MonadError[G]
  ): RequestT[U, T, R0 with Effect[G]] = {
    RequestT(
      r.method,
      r.uri,
      r.body.asInstanceOf[RequestBody[R0 with Effect[G]]], // request body can't use the Effect capability
      r.headers,
      apply[T, R0, F, G](
        r.response.asInstanceOf[ResponseAs[T, R0 with Effect[F]]], // this is witnessed by rHasEffectF
        fk,
        gk,
        fm,
        gm
      ),
      r.options,
      r.tags
    )
  }

  private def apply[TT, R0, F[_], G[_]](
      r: ResponseAs[TT, R0 with Effect[F]],
      fk: FunctionK[F, G],
      gk: FunctionK[G, F],
      fm: MonadError[F],
      gm: MonadError[G]
  ): ResponseAs[TT, R0 with Effect[G]] = {
    r match {
      case IgnoreResponse      => IgnoreResponse
      case ResponseAsByteArray => ResponseAsByteArray
      case ResponseAsStream(s, f) =>
        ResponseAsStream(s, f.asInstanceOf[Any => F[Any]].andThen(fk.apply(_)))
          .asInstanceOf[ResponseAs[TT, R0 with Effect[G]]]
      case ResponseAsStreamUnsafe(s) => ResponseAsStreamUnsafe(s)
      case ResponseAsFile(output)    => ResponseAsFile(output)
      case ResponseAsWebSocket(f) =>
        ResponseAsWebSocket((wg: WebSocket[G]) => fk(f.asInstanceOf[WebSocket[F] => F[TT]](apply[G, F](wg, gk, fm))))
          .asInstanceOf[ResponseAs[TT, R0 with Effect[G]]]
      case ResponseAsWebSocketUnsafe() => ResponseAsWebSocketUnsafe().asInstanceOf[ResponseAs[TT, R0 with Effect[G]]]
      case ResponseAsWebSocketStream(s, p) =>
        ResponseAsWebSocketStream(s, p).asInstanceOf[ResponseAs[TT, R0 with Effect[G]]]
      case ResponseAsFromMetadata(conditions, default) =>
        ResponseAsFromMetadata[TT, R0 with Effect[G]](
          conditions.map(c => ConditionalResponseAs(c.condition, apply[TT, R0, F, G](c.responseAs, fk, gk, fm, gm))),
          apply[TT, R0, F, G](default, fk, gk, fm, gm)
        )
      case MappedResponseAs(raw, g) =>
        MappedResponseAs(apply[Any, R0, F, G](raw, fk, gk, fm, gm), g).asInstanceOf[ResponseAs[TT, R0 with Effect[G]]]
    }
  }

  private def apply[F[_], G[_]](ws: WebSocket[F], fk: FunctionK[F, G], gm: MonadError[G]): WebSocket[G] =
    new WebSocket[G] {
      override def receive: G[Either[WebSocketFrame.Close, WebSocketFrame.Incoming]] = fk(ws.receive)
      override def send(f: WebSocketFrame, isContinuation: Boolean): G[Unit] = fk(ws.send(f, isContinuation))
      override def isOpen: G[Boolean] = fk(ws.isOpen)
      override implicit def monad: MonadError[G] = gm
    }
}
