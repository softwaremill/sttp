package sttp.client.ws

import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.model.ws.WebSocketFrame

import scala.language.higherKinds

/**
  * The `send` and `receive` methods may result in a failed effect, with either one of [[sttp.model.ws.WebSocketError]]
  * exceptions, or a backend-specific exception.
  */
trait WebSocket[F[_]] {
  def receive: F[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]]
  def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit]
  def isOpen: F[Boolean]

  /**
    * Receive a single data frame, ignoring others. The frame might be a fragment.
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveDataFrame(pongOnPing: Boolean = true): F[Either[WebSocketEvent.Close, WebSocketFrame.Data]] =
    receive.flatMap {
      case Left(close)                   => (Left(close): Either[WebSocketEvent.Close, WebSocketFrame.Data]).unit
      case Right(d: WebSocketFrame.Data) => (Right(d): Either[WebSocketEvent.Close, WebSocketFrame.Data]).unit
      case Right(WebSocketFrame.Ping(payload)) if pongOnPing =>
        send(WebSocketFrame.Pong(payload)).flatMap(_ => receiveDataFrame(pongOnPing))
      case _ => receiveDataFrame(pongOnPing)
    }

  /**
    * Receive a single text data frame, ignoring others. The frame might be a fragment. To receive whole messages,
    * use [[receiveText]].
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveTextFrame(pongOnPing: Boolean = true): F[Either[WebSocketEvent.Close, WebSocketFrame.Text]] =
    receiveDataFrame(pongOnPing).flatMap {
      case Left(close)                   => (Left(close): Either[WebSocketEvent.Close, WebSocketFrame.Text]).unit
      case Right(t: WebSocketFrame.Text) => (Right(t): Either[WebSocketEvent.Close, WebSocketFrame.Text]).unit
      case _                             => receiveTextFrame(pongOnPing)
    }

  /**
    * Receive a single binary data frame, ignoring others. The frame might be a fragment. To receive whole messages,
    * use [[receiveBinary]].
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveBinaryFrame(pongOnPing: Boolean = true): F[Either[WebSocketEvent.Close, WebSocketFrame.Binary]] =
    receiveDataFrame(pongOnPing).flatMap {
      case Left(close)                     => (Left(close): Either[WebSocketEvent.Close, WebSocketFrame.Binary]).unit
      case Right(t: WebSocketFrame.Binary) => (Right(t): Either[WebSocketEvent.Close, WebSocketFrame.Binary]).unit
      case _                               => receiveBinaryFrame(pongOnPing)
    }

  /**
    * Receive a single text message (which might come from multiple, fragmented frames).
    * Ignores non-text frames and returns combined results.
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveText(pongOnPing: Boolean = true): F[Either[WebSocketEvent.Close, String]] = {
    receiveTextFrame(pongOnPing)
      .flatMap {
        case Left(close) => (Left(close): Either[WebSocketEvent.Close, String]).unit
        case Right(WebSocketFrame.Text(payload, false, _)) =>
          receiveText(pongOnPing).flatMap {
            case Left(close) => (Left(close): Either[WebSocketEvent.Close, String]).unit
            case Right(t)    => (Right(payload + t): Either[WebSocketEvent.Close, String]).unit
          }
        case Right(WebSocketFrame.Text(payload, true, _)) => (Right(payload): Either[WebSocketEvent.Close, String]).unit
      }
  }

  /**
    * Receive a single binary message (which might come from multiple, fragmented frames).
    * Ignores non-binary frames and returns combined results.
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveBinary(pongOnPing: Boolean): F[Either[WebSocketEvent.Close, Array[Byte]]] = {
    receiveBinaryFrame(pongOnPing).flatMap {
      case Left(close) => (Left(close): Either[WebSocketEvent.Close, Array[Byte]]).unit
      case Right(WebSocketFrame.Binary(payload, false, _)) =>
        receiveBinary(pongOnPing).flatMap {
          case Left(close) => (Left(close): Either[WebSocketEvent.Close, Array[Byte]]).unit
          case Right(t)    => (Right(payload ++ t): Either[WebSocketEvent.Close, Array[Byte]]).unit
        }
      case Right(WebSocketFrame.Binary(payload, true, _)) =>
        (Right(payload): Either[WebSocketEvent.Close, Array[Byte]]).unit
    }
  }

  def close: F[Unit] = send(WebSocketFrame.close)

  implicit def monad: MonadError[F]
}
