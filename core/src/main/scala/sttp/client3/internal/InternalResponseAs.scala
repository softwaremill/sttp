package sttp.client3.internal

import sttp.capabilities.Effect
import sttp.capabilities.WebSockets
import sttp.client3._
import sttp.model.ResponseMetadata
import sttp.model.internal.Rfc3986
import sttp.ws.WebSocket

import scala.collection.immutable.Seq
import sttp.capabilities.Streams
import sttp.ws.WebSocketFrame

/** Internal representation of how the response of an [[sttp.client3.AbstractRequest]] should be handled.
  *
  * @tparam T
  *   Target type as which the response will be read.
  * @tparam R
  *   The backend capabilities required by the response description. This might be `Any` (no requirements), [[Effect]]
  *   (the backend must support the given effect type), [[Streams]] (the ability to send and receive streaming bodies)
  *   or [[WebSockets]] (the ability to handle websocket requests).
  */
sealed trait InternalResponseAs[+T, -R] {
  def map[T2](f: T => T2): InternalResponseAs[T2, R] = mapWithMetadata { case (t, _) => f(t) }
  def mapWithMetadata[T2](f: (T, ResponseMetadata) => T2): InternalResponseAs[T2, R] =
    MappedResponseAs[T, T2, R](this, f, None)

  def show: String
  def showAs(s: String): InternalResponseAs[T, R] = MappedResponseAs[T, T, R](this, (t, _) => t, Some(s))
}

case object IgnoreResponse extends InternalResponseAs[Unit, Any] {
  override def show: String = "ignore"
}
case object ResponseAsByteArray extends InternalResponseAs[Array[Byte], Any] {
  override def show: String = "as byte array"
}

// Path-dependent types are not supported in constructor arguments or the extends clause. Thus we cannot express the
// fact that `BinaryStream =:= s.BinaryStream`. We have to rely on correct construction via the companion object and
// perform typecasts when the request is deconstructed.
case class ResponseAsStream[F[_], T, Stream, S] private (s: Streams[S], f: (Stream, ResponseMetadata) => F[T])
    extends InternalResponseAs[T, S with Effect[F]] {
  override def show: String = "as stream"
}
object ResponseAsStream {
  def apply[F[_], T, S](s: Streams[S])(
      f: (s.BinaryStream, ResponseMetadata) => F[T]
  ): InternalResponseAs[T, S with Effect[F]] =
    new ResponseAsStream(s, f)
}

case class ResponseAsStreamUnsafe[BinaryStream, S] private (s: Streams[S]) extends InternalResponseAs[BinaryStream, S] {
  override def show: String = "as stream unsafe"
}
object ResponseAsStreamUnsafe {
  def apply[S](s: Streams[S]): InternalResponseAs[s.BinaryStream, S] = new ResponseAsStreamUnsafe(s)
}

case class ResponseAsFile(output: SttpFile) extends InternalResponseAs[SttpFile, Any] {
  override def show: String = s"as file: ${output.name}"
}

sealed trait InternalWebSocketResponseAs[T, -R] extends InternalResponseAs[T, R]
case class ResponseAsWebSocket[F[_], T](f: (WebSocket[F], ResponseMetadata) => F[T])
    extends InternalWebSocketResponseAs[T, WebSockets with Effect[F]] {
  override def show: String = "as web socket"
}
case class ResponseAsWebSocketUnsafe[F[_]]()
    extends InternalWebSocketResponseAs[WebSocket[F], WebSockets with Effect[F]] {
  override def show: String = "as web socket unsafe"
}
case class ResponseAsWebSocketStream[S, Pipe[_, _]](s: Streams[S], p: Pipe[WebSocketFrame.Data[_], WebSocketFrame])
    extends InternalWebSocketResponseAs[Unit, S with WebSockets] {
  override def show: String = "as web socket stream"
}

case class ResponseAsFromMetadata[T, R](
    conditions: List[ConditionalResponseAs[InternalResponseAs[T, R]]],
    default: InternalResponseAs[T, R]
) extends InternalResponseAs[T, R] {
  def apply(meta: ResponseMetadata): InternalResponseAs[T, R] =
    conditions.find(mapping => mapping.condition(meta)).map(_.responseAs).getOrElse(default)
  override def show: String = s"either(${(default.show :: conditions.map(_.responseAs.show)).mkString(", ")})"
}

case class MappedResponseAs[T, T2, R](
    raw: InternalResponseAs[T, R],
    g: (T, ResponseMetadata) => T2,
    showAs: Option[String]
) extends InternalResponseAs[T2, R] {
  override def mapWithMetadata[T3](f: (T2, ResponseMetadata) => T3): InternalResponseAs[T3, R] =
    MappedResponseAs[T, T3, R](raw, (t, h) => f(g(t, h), h), showAs.map(s => s"mapped($s)"))
  override def showAs(s: String): InternalResponseAs[T2, R] = this.copy(showAs = Some(s))

  override def show: String = showAs.getOrElse(s"mapped(${raw.show})")
}

case class ResponseAsBoth[A, B, R](l: InternalResponseAs[A, R], r: InternalResponseAs[B, Any])
    extends InternalResponseAs[(A, Option[B]), R] {
  override def show: String = s"(${l.show}, optionally ${r.show})"
}

object InternalResponseAs {

  private[client3] def parseParams(s: String, charset: String): Seq[(String, String)] = {
    s.split("&")
      .toList
      .flatMap(kv =>
        kv.split("=", 2) match {
          case Array(k, v) =>
            Some((Rfc3986.decode()(k, charset), Rfc3986.decode()(v, charset)))
          case _ => None
        }
      )
  }

  def isWebSocket[T, R](ra: InternalResponseAs[_, _]): Boolean =
    ra match {
      case _: InternalWebSocketResponseAs[_, _] => true
      case ResponseAsFromMetadata(conditions, default) =>
        conditions.exists(c => isWebSocket(c.responseAs)) || isWebSocket(default)
      case MappedResponseAs(raw, _, _) => isWebSocket(raw)
      case ResponseAsBoth(l, r)        => isWebSocket(l) || isWebSocket(r)
      case _                           => false
    }
}
