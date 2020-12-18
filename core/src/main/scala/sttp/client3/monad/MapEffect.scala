package sttp.client3.monad

import sttp.capabilities.Effect
import sttp.client3.{
  ConditionalResponseAs,
  IgnoreResponse,
  MappedResponseAs,
  RequestBody,
  RequestT,
  ResponseAs,
  ResponseAsBoth,
  ResponseAsByteArray,
  ResponseAsFile,
  ResponseAsFromMetadata,
  ResponseAsStream,
  ResponseAsStreamUnsafe,
  ResponseAsWebSocket,
  ResponseAsWebSocketStream,
  ResponseAsWebSocketUnsafe,
  ResponseMetadata
}
import sttp.model.Headers
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}

object MapEffect {

  /** Change the effect type that's used by the response specification of this request, if the response specification
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
    if (usesEffect(r.response)) {
      RequestT(
        r.method,
        r.uri,
        r.body.asInstanceOf[RequestBody[R0 with Effect[G]]], // request body can't use the Effect capability
        r.headers,
        apply[F, G](
          r.response, // this is witnessed by rHasEffectF
          fk,
          gk,
          fm,
          gm
        ).asInstanceOf[ResponseAs[T, R0 with Effect[G]]],
        r.options,
        r.tags
      )
    } else {
      r.asInstanceOf[RequestT[U, T, R0 with Effect[G]]]
    }
  }

  // TODO: an even more dumbed-down version of the slightly more type-safe version below, which is needed due to a
  // TODO: bug in Dotty: https://github.com/lampepfl/dotty/issues/9533
  private def apply[F[_], G[_]](
      r: ResponseAs[_, _],
      fk: FunctionK[F, G],
      gk: FunctionK[G, F],
      fm: MonadError[F],
      gm: MonadError[G]
  ): ResponseAs[_, _] = {
    r match {
      case IgnoreResponse      => IgnoreResponse
      case ResponseAsByteArray => ResponseAsByteArray
      case ResponseAsStream(s, f) =>
        ResponseAsStream(s)((s, m) => fk(f.asInstanceOf[(Any, ResponseMetadata) => F[Any]](s, m)))
      case rasu: ResponseAsStreamUnsafe[_, _] => rasu
      case ResponseAsFile(output)             => ResponseAsFile(output)
      case ResponseAsWebSocket(f) =>
        ResponseAsWebSocket((wg: WebSocket[G], m: ResponseMetadata) =>
          fk(f.asInstanceOf[(WebSocket[F], ResponseMetadata) => F[Any]](apply[G, F](wg, gk, fm), m))
        )
      case ResponseAsWebSocketUnsafe() => ResponseAsWebSocketUnsafe()
      case ResponseAsWebSocketStream(s, p) =>
        ResponseAsWebSocketStream(s, p)
      case ResponseAsFromMetadata(conditions, default) =>
        ResponseAsFromMetadata(
          conditions.map(c =>
            ConditionalResponseAs[Any, Any](
              c.condition,
              apply[F, G](c.responseAs, fk, gk, fm, gm).asInstanceOf[ResponseAs[Any, Any]]
            )
          ),
          apply[F, G](default, fk, gk, fm, gm).asInstanceOf[ResponseAs[Any, Any]]
        )
      case MappedResponseAs(raw, g, showAs) =>
        MappedResponseAs(apply[F, G](raw, fk, gk, fm, gm), g.asInstanceOf[(Any, ResponseMetadata) => Any], showAs)
      case ResponseAsBoth(l, r) =>
        ResponseAsBoth(apply(l, fk, gk, fm, gm), apply(r, fk, gk, fm, gm).asInstanceOf[ResponseAs[_, Any]])
    }
  }

  /*
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
   */

  private def apply[F[_], G[_]](ws: WebSocket[F], fk: FunctionK[F, G], gm: MonadError[G]): WebSocket[G] =
    new WebSocket[G] {
      override def receive(): G[WebSocketFrame] = fk(ws.receive())
      override def send(f: WebSocketFrame, isContinuation: Boolean): G[Unit] = fk(ws.send(f, isContinuation))
      override def upgradeHeaders: Headers = ws.upgradeHeaders
      override def isOpen(): G[Boolean] = fk(ws.isOpen())
      override implicit def monad: MonadError[G] = gm
    }

  private def usesEffect(ra: ResponseAs[_, _]): Boolean =
    ra match {
      case ResponseAsWebSocket(_)      => true
      case ResponseAsWebSocketUnsafe() => true
      case ResponseAsStream(_, _)      => true
      case ResponseAsFromMetadata(conditions, default) =>
        usesEffect(default) || conditions.exists(c => usesEffect(c.responseAs))
      case MappedResponseAs(raw, _, _) => usesEffect(raw)
      case _                           => false
    }
}
