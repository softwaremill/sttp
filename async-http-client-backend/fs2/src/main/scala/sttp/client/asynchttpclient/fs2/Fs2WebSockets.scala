package sttp.client.asynchttpclient.fs2

import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Ref
import cats.effect.implicits._
import fs2.{Pipe, Stream}
import sttp.client.ws.{WebSocket, WebSocketEvent}
import sttp.model.ws.WebSocketFrame

object Fs2WebSockets {

  /**
    * Handle the websocket through a [[Pipe]] which receives the incoming events and produces the messages to be sent
    * to the server. Not that by the nature of a [[Pipe]], there no need that these two streams are coupled. Just make sure
    * to consume the input as otherwise the receiving buffer might overflow (use [[Stream.drain]] if you want to discard).
    * Messages to be sent need to be coupled with a Boolean indicating whether this frame is a continuation, see [[WebSocket.send()]].
    * @param ws the websocket to handle
    * @param sendPongOnPing if true, then automatically handle sending pongs on pings (which are then not passed to the pipe)
    * @param pipe the pipe to handle the socket
    * @tparam F the effect type
    * @return an Unit effect describing the full run of the websocket through the pipe
    */
  def handleSocketThroughPipeWithFragmentation[F[_]: ConcurrentEffect](
      ws: WebSocket[F],
      sendPongOnPing: Boolean = true
  )(pipe: Pipe[F, WebSocketFrame.Incoming, (WebSocketFrame, Boolean)]): F[Unit] = {
    Stream
      .eval(Ref.of[F, Option[WebSocketFrame.Close]](None))
      .flatMap { closeRef =>
        Stream
          .repeatEval(ws.receive) // read incoming messages
          .flatMap {
            case Left(WebSocketEvent.Close(code, reason)) =>
              Stream.eval(closeRef.set(Some(WebSocketFrame.Close(code, reason)))).as(None)
            case Right(WebSocketFrame.Ping(payload)) if sendPongOnPing =>
              Stream.eval(ws.send(WebSocketFrame.Pong(payload))).drain
            case Right(in) => Stream.emit(Some(in))
          }
          .unNoneTerminate // terminate once we got a Close
          .through(pipe)
          // end with matching Close or user-provided Close or no Close at all
          .append(Stream.eval(closeRef.get).unNone.map(_ -> false)) // A Close isn't a continuation
          .evalMap { case (m, c) => ws.send(m, c) } // send messages
      }
      .compile
      .drain
      .guarantee(ws.close)
  }

  /**
    * Same as [[handleSocketThroughPipeWithFragmentation()]], but assumes all frames are no continuations.
    * @param ws the websocket to handle
    * @param sendPongOnPing if true, then automatically handle sending pongs on pings (which are then not passed to the pipe)
    * @param pipe the pipe to handle the socket
    * @tparam F the effect type
    * @return an Unit effect describing the full run of the websocket through the pipe
    */
  def handleSocketThroughPipe[F[_]: ConcurrentEffect](
      ws: WebSocket[F],
      sendPongOnPing: Boolean = true
  )(pipe: Pipe[F, WebSocketFrame.Incoming, WebSocketFrame]): F[Unit] =
    handleSocketThroughPipeWithFragmentation[F](ws, sendPongOnPing)(pipe.andThen(_.map(_ -> false)))

  /**
    * Like [[handleSocketThroughPipe()]] but extracts the contents of incoming text messages and wraps outgoing messages
    * in text frames. Helpful for implementing text-only protocols.
    * Note: It always sends a pong for any received ping.
    * @param ws the websocket to handle
    * @param pipe the pipe to handle the socket
    * @tparam F the effect type
    * @return an Unit effect describing the full run of the websocket through the pipe
    */
  def handleSocketThroughTextPipe[F[_]: ConcurrentEffect](
      ws: WebSocket[F]
  )(pipe: Pipe[F, String, Either[WebSocketFrame.Close, String]]): F[Unit] =
    handleSocketThroughPipe[F](ws, sendPongOnPing = true) {
      pipe
        .compose[Stream[F, WebSocketFrame.Incoming]](_.collect { case WebSocketFrame.Text(msg, _, _) => msg })
        .andThen(_.map(_.fold(identity, WebSocketFrame.text)))
    }
}
