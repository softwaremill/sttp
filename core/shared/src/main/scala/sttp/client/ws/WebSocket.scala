package sttp.client.ws

import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.model.ws.WebSocketFrame

import scala.language.higherKinds

trait WebSocket[F[_]] {
  def receive: F[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]]
  def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit]
  def isOpen: F[Boolean]

  /**
    * Receive data frames, ignoring others.
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
    * Receive text data frames, ignoring others.
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveTextFrame(pongOnPing: Boolean = true): F[Either[WebSocketEvent.Close, WebSocketFrame.Text]] =
    receiveDataFrame(pongOnPing).flatMap {
      case Left(close)                   => (Left(close): Either[WebSocketEvent.Close, WebSocketFrame.Text]).unit
      case Right(t: WebSocketFrame.Text) => (Right(t): Either[WebSocketEvent.Close, WebSocketFrame.Text]).unit
      case _                             => receiveTextFrame(pongOnPing)
    }

  /**
    * Receive binary data frames, ignoring others.
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveBinaryFrame(pongOnPing: Boolean = true): F[Either[WebSocketEvent.Close, WebSocketFrame.Binary]] =
    receiveDataFrame(pongOnPing).flatMap {
      case Left(close)                     => (Left(close): Either[WebSocketEvent.Close, WebSocketFrame.Binary]).unit
      case Right(t: WebSocketFrame.Binary) => (Right(t): Either[WebSocketEvent.Close, WebSocketFrame.Binary]).unit
      case _                               => receiveBinaryFrame(pongOnPing)
    }

  /**
    * Accumulates text data frames ignoring others and returns combined results to the user
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
    * Accumulates text data frames ignoring others and returns combined results to the user
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
