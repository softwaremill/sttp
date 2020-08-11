package sttp.client.ws

import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.model.ws.WebSocketFrame

/**
  * The `send` and `receive` methods may result in a failed effect, with either one of [[sttp.model.ws.WebSocketException]]
  * exceptions, or a backend-specific exception.
  */
trait WebSocket[F[_]] {

  /**
    * After receiving a close frame, no further interactions with the web socket should happen. Subsequent invocations
    * of `receive`, as well as `send`, will fail with the [[sttp.model.ws.WebSocketClosed]] exception.
    */
  def receive: F[WebSocketFrame]
  def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit]
  def isOpen: F[Boolean]

  /**
    * Receive a single data frame, ignoring others. The frame might be a fragment.
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveDataFrame(pongOnPing: Boolean = true): F[Either[WebSocketFrame.Close, WebSocketFrame.Data[_]]] =
    receive.flatMap {
      case close: WebSocketFrame.Close => (Left(close): Either[WebSocketFrame.Close, WebSocketFrame.Data[_]]).unit
      case d: WebSocketFrame.Data[_]   => (Right(d): Either[WebSocketFrame.Close, WebSocketFrame.Data[_]]).unit
      case WebSocketFrame.Ping(payload) if pongOnPing =>
        send(WebSocketFrame.Pong(payload)).flatMap(_ => receiveDataFrame(pongOnPing))
      case _ => receiveDataFrame(pongOnPing)
    }

  /**
    * Receive a single text data frame, ignoring others. The frame might be a fragment. To receive whole messages,
    * use [[receiveText]].
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveTextFrame(pongOnPing: Boolean = true): F[Either[WebSocketFrame.Close, WebSocketFrame.Text]] =
    receiveDataFrame(pongOnPing).flatMap {
      case Left(close)                   => (Left(close): Either[WebSocketFrame.Close, WebSocketFrame.Text]).unit
      case Right(t: WebSocketFrame.Text) => (Right(t): Either[WebSocketFrame.Close, WebSocketFrame.Text]).unit
      case _                             => receiveTextFrame(pongOnPing)
    }

  /**
    * Receive a single binary data frame, ignoring others. The frame might be a fragment. To receive whole messages,
    * use [[receiveBinary]].
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveBinaryFrame(pongOnPing: Boolean = true): F[Either[WebSocketFrame.Close, WebSocketFrame.Binary]] =
    receiveDataFrame(pongOnPing).flatMap {
      case Left(close)                     => (Left(close): Either[WebSocketFrame.Close, WebSocketFrame.Binary]).unit
      case Right(t: WebSocketFrame.Binary) => (Right(t): Either[WebSocketFrame.Close, WebSocketFrame.Binary]).unit
      case _                               => receiveBinaryFrame(pongOnPing)
    }

  /**
    * Receive a single text message (which might come from multiple, fragmented frames).
    * Ignores non-text frames and returns combined results.
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveText(pongOnPing: Boolean = true): F[Either[WebSocketFrame.Close, String]] =
    receiveConcat(() => receiveTextFrame(pongOnPing), _ + _)

  /**
    * Receive a single binary message (which might come from multiple, fragmented frames).
    * Ignores non-binary frames and returns combined results.
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveBinary(pongOnPing: Boolean): F[Either[WebSocketFrame.Close, Array[Byte]]] =
    receiveConcat(() => receiveBinaryFrame(pongOnPing), _ ++ _)

  private def receiveConcat[T, U <: WebSocketFrame.Data[T]](
      receiveSingle: () => F[Either[WebSocketFrame.Close, U]],
      combine: (T, T) => T
  ): F[Either[WebSocketFrame.Close, T]] = {
    receiveSingle().flatMap {
      case Left(close) => (Left(close): Either[WebSocketFrame.Close, T]).unit
      case Right(data) if !data.finalFragment =>
        receiveConcat(receiveSingle, combine).flatMap {
          case Left(close) => (Left(close): Either[WebSocketFrame.Close, T]).unit
          case Right(t)    => (Right(combine(data.payload, t)): Either[WebSocketFrame.Close, T]).unit
        }
      case Right(data) if data.finalFragment =>
        (Right(data.payload): Either[WebSocketFrame.Close, T]).unit
    }
  }

  /**
    * Idempotent when used sequentially.
    */
  def close: F[Unit] = send(WebSocketFrame.close)

  implicit def monad: MonadError[F]
}
