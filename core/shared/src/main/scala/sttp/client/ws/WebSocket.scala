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
  def receiveData(pongOnPing: Boolean = true): F[Either[WebSocketEvent.Close, WebSocketFrame.Data]] = receive.flatMap {
    case Left(close)                   => (Left(close): Either[WebSocketEvent.Close, WebSocketFrame.Data]).unit
    case Right(d: WebSocketFrame.Data) => (Right(d): Either[WebSocketEvent.Close, WebSocketFrame.Data]).unit
    case Right(WebSocketFrame.Ping(payload)) if pongOnPing =>
      send(WebSocketFrame.Pong(payload)).flatMap(_ => receiveData(pongOnPing))
    case _ => receiveData(pongOnPing)
  }

  /**
    * Receive text data frames, ignoring others.
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveText(pongOnPing: Boolean = true): F[Either[WebSocketEvent.Close, WebSocketFrame.Text]] =
    receiveData(pongOnPing).flatMap {
      case Left(close)                   => (Left(close): Either[WebSocketEvent.Close, WebSocketFrame.Text]).unit
      case Right(t: WebSocketFrame.Text) => (Right(t): Either[WebSocketEvent.Close, WebSocketFrame.Text]).unit
      case _                             => receiveText(pongOnPing)
    }

  /**
    * Receive binary data frames, ignoring others.
    * @param pongOnPing Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveBinary(pongOnPing: Boolean = true): F[Either[WebSocketEvent.Close, WebSocketFrame.Binary]] =
    receiveData(pongOnPing).flatMap {
      case Left(close)                     => (Left(close): Either[WebSocketEvent.Close, WebSocketFrame.Binary]).unit
      case Right(t: WebSocketFrame.Binary) => (Right(t): Either[WebSocketEvent.Close, WebSocketFrame.Binary]).unit
      case _                               => receiveBinary(pongOnPing)
    }

  def close: F[Unit] = send(WebSocketFrame.close)

  implicit def monad: MonadError[F]
}
