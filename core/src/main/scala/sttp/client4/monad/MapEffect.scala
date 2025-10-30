package sttp.client4.monad

import sttp.capabilities.{Effect, WebSockets}
import sttp.client4._
import sttp.model.{Headers, ResponseMetadata}
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}

object MapEffect {

  /** Change the effect type that's used by the response specification of this request, if the response specification
    * requires the `Effect[F]` capability.
    * @param fk
    *   A transformation between effects `F` and `G`
    * @tparam F
    *   The source effect type.
    * @tparam G
    *   The target effect type.
    * @tparam R0
    *   The requirements of this request, without the `Effect[F]` capability.
    */
  def apply[F[_], G[_], T, R0](
      r: GenericRequest[T, R0 with Effect[F]],
      fk: FunctionK[F, G],
      gk: FunctionK[G, F],
      fm: MonadError[F],
      gm: MonadError[G]
  ): GenericRequest[T, R0 with Effect[G]] = {
    def internalResponse[R] = apply[F, G](r.response.delegate, fk, gk, fm, gm)
      .asInstanceOf[GenericResponseAs[T, R with Effect[G]]]

    // Only StreamRequest and WebSocketRequest can have an effectful response
    val newRequest = r match {
      case srf: StreamRequest[_, R0 with Effect[F]] =>
        srf.copy(
          body = srf.body.asInstanceOf[GenericRequestBody[R0]],
          response = new StreamResponseAs(internalResponse[R0])
        )
      case wr: WebSocketRequest[_, _] =>
        wr.copy[G, T](response = new WebSocketResponseAs[G, T](internalResponse[WebSockets]))
      case _ => r
    }
    newRequest.asInstanceOf[GenericRequest[T, R0 with Effect[G]]]
  }

  // TODO: an even more dumbed-down version of the slightly more type-safe version below, which is needed due to a
  // TODO: bug in Dotty: https://github.com/lampepfl/dotty/issues/9533
  private def apply[F[_], G[_]](
      r: GenericResponseAs[_, _],
      fk: FunctionK[F, G],
      gk: FunctionK[G, F],
      fm: MonadError[F],
      gm: MonadError[G]
  ): GenericResponseAs[_, _] =
    r match {
      case IgnoreResponse         => IgnoreResponse
      case ResponseAsByteArray    => ResponseAsByteArray
      case ResponseAsStream(s, f) =>
        ResponseAsStream(s)((s, m) => fk(f.asInstanceOf[(Any, ResponseMetadata) => F[Any]](s, m)))
      case rasu: ResponseAsStreamUnsafe[_, _] => rasu
      case ResponseAsFile(output)             => ResponseAsFile(output)
      case ResponseAsInputStream(f)           => ResponseAsInputStream(f)
      case ResponseAsInputStreamUnsafe        => ResponseAsInputStreamUnsafe
      case ResponseAsWebSocket(f)             =>
        ResponseAsWebSocket((wg: WebSocket[G], m: ResponseMetadata) =>
          fk(f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[Any]](apply[G, F](wg, gk, fm), m))
        )
      case ResponseAsWebSocketUnsafe()     => ResponseAsWebSocketUnsafe()
      case ResponseAsWebSocketStream(s, p) =>
        ResponseAsWebSocketStream(s, p)
      case ResponseAsFromMetadata(conditions, default) =>
        ResponseAsFromMetadata[Any, Any](
          conditions.map(c =>
            ConditionalResponseAs(
              c.condition,
              apply[F, G](c.responseAs, fk, gk, fm, gm).asInstanceOf[GenericResponseAs[Any, Any]]
            )
          ),
          apply[F, G](default, fk, gk, fm, gm).asInstanceOf[GenericResponseAs[Any, Any]]
        )
      case MappedResponseAs(raw, g, showAs) =>
        MappedResponseAs(apply[F, G](raw, fk, gk, fm, gm), g.asInstanceOf[(Any, ResponseMetadata) => Any], showAs)
      case ResponseAsBoth(l, r) =>
        ResponseAsBoth(apply(l, fk, gk, fm, gm), apply(r, fk, gk, fm, gm).asInstanceOf[GenericResponseAs[_, Any]])
    }

  /* private def apply[TT, R0, F[_], G[_]](
      r: InternalResponseAs[TT, R0 with Effect[F]],
      fk: FunctionK[F, G],
      gk: FunctionK[G, F],
      fm: MonadError[F],
      gm: MonadError[G]
  ): InternalResponseAs[TT, R0 with Effect[G]] = {
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
  } */

  private def apply[F[_], G[_]](ws: WebSocket[F], fk: FunctionK[F, G], gm: MonadError[G]): WebSocket[G] =
    new WebSocket[G] {
      override def receive(): G[WebSocketFrame] = fk(ws.receive())
      override def send(f: WebSocketFrame, isContinuation: Boolean): G[Unit] = fk(ws.send(f, isContinuation))
      override def upgradeHeaders: Headers = ws.upgradeHeaders
      override def isOpen(): G[Boolean] = fk(ws.isOpen())
      override implicit def monad: MonadError[G] = gm
    }
}
