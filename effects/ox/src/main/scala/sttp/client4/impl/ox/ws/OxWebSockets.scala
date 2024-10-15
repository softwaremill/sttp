package sttp.client4.impl.ox.ws

import ox.*
import ox.channels.*
import sttp.client4.ws.SyncWebSocket
import sttp.ws.WebSocketFrame

import scala.util.control.NonFatal
import ox.flow.Flow

/** Converts a [[SyncWebSocket]] into a pair of [[Source]] of server responses and a [[Sink]] for client requests. The
  * `Source` starts receiving frames immediately, its internal buffer size can be adjusted with an implicit
  * [[ox.channels.BufferCapacity]]. Make sure that the `Source` is contiunually read. This will guarantee that
  * server-side close signal is received and handled. If you don't want to process frames from the server, you can at
  * least handle it with a `fork { source.drain() }`.
  *
  * You don't need to manually call `ws.close()` when using this approach, this will be handled automatically
  * underneath, according to following rules:
  *   - If the request sink is closed due to an upstream error, a close frame is sent. The response sink keeps receiving
  *     responses, until the enclosing [[Ox]] scope ends (that is controlled by the caller). When this happens, the fork
  *     which populates the response channel will be interrupted.
  *   - If the request sink completes as done, a close frame is sent. As above, the response sink keeps receiving
  *     responses until the server closes communication.
  *   - If the response source is closed by a close frame from the server or due to an error, the request sink is closed
  *     as done. This will attempt to send all outstanding buffered frames, unless the enclosing scope ends beforehand).
  *
  * @param ws
  *   a `SyncWebSocket` where the underlying `Sink` will send requests, and where the `Source` will pull responses from.
  * @param concatenateFragmented
  *   whether fragmented frames from the server should be concatenated into a single frame (true by default).
  */
def asSourceAndSink(ws: SyncWebSocket, concatenateFragmented: Boolean = true)(using
    Ox,
    BufferCapacity
): (Source[WebSocketFrame], Sink[WebSocketFrame]) =
  val requestsChannel = BufferCapacity.newChannel[WebSocketFrame]

  val responsesChannel = Flow
    .usingEmit[WebSocketFrame] { emit =>
      repeatWhile {
        ws.receive() match
          case frame: WebSocketFrame.Data[_]                      => emit(frame); true
          case WebSocketFrame.Close(status, msg) if status > 1001 => throw new WebSocketClosedWithError(status, msg)
          case _: WebSocketFrame.Close                            => false
          case ping: WebSocketFrame.Ping =>
            requestsChannel.sendOrClosed(WebSocketFrame.Pong(ping.payload)).discard
            // Keep receiving even if pong couldn't be sent due to closed request channel. We want to process
            // whatever responses there are still coming from the server until it signals the end with a Close frome.
            true
          case _: WebSocketFrame.Pong =>
            // ignore pongs
            true
      }
    }
    .pipe(optionallyConcatenateFrames(_, concatenateFragmented))
    .onComplete(requestsChannel.doneOrClosed().discard)
    .runToChannel()

  fork {
    try
      repeatWhile {
        requestsChannel.receiveOrClosed() match
          case closeFrame: WebSocketFrame.Close =>
            ws.send(closeFrame)
            false
          case frame: WebSocketFrame =>
            ws.send(frame)
            true
          case ChannelClosed.Done =>
            ws.close()
            false
          case ChannelClosed.Error(err) =>
            // There's no proper "client error" status. Statuses 4000+ are available for custom cases
            ws.send(WebSocketFrame.Close(4000, "Client error"))
            // Assuming the responsesChannel fork will get interrupted because the enclosing scope will end
            false
      }
    catch
      case NonFatal(err) =>
        // If responses are closed, server finished the communication and we can ignore that send() failed
        if (!responsesChannel.isClosedForReceive) requestsChannel.errorOrClosed(err).discard
  }.discard

  (responsesChannel, requestsChannel)

final case class WebSocketClosedWithError(statusCode: Int, msg: String)
    extends Exception(s"WebSocket closed with status $statusCode: $msg")

private def optionallyConcatenateFrames(f: Flow[WebSocketFrame], doConcatenate: Boolean)(using
    Ox
): Flow[WebSocketFrame] =
  if doConcatenate then
    type Accumulator = Option[Either[Array[Byte], String]]
    f.mapStateful(() => None: Accumulator) {
      case (None, f: WebSocketFrame.Ping)                       => (None, Some(f))
      case (None, f: WebSocketFrame.Pong)                       => (None, Some(f))
      case (None, f: WebSocketFrame.Close)                      => (None, Some(f))
      case (None, f: WebSocketFrame.Data[_]) if f.finalFragment => (None, Some(f))
      case (None, f: WebSocketFrame.Text)                       => (Some(Right(f.payload)), None)
      case (None, f: WebSocketFrame.Binary)                     => (Some(Left(f.payload)), None)
      case (Some(Left(acc)), f: WebSocketFrame.Binary) if f.finalFragment =>
        (None, Some(f.copy(payload = acc ++ f.payload)))
      case (Some(Left(acc)), f: WebSocketFrame.Binary) if !f.finalFragment => (Some(Left(acc ++ f.payload)), None)
      case (Some(Right(acc)), f: WebSocketFrame.Text) if f.finalFragment =>
        (None, Some(f.copy(payload = acc + f.payload)))
      case (Some(Right(acc)), f: WebSocketFrame.Text) if !f.finalFragment => (Some(Right(acc + f.payload)), None)
      case (acc, f) =>
        throw new IllegalStateException(
          s"Unexpected WebSocket frame received during concatenation. Frame received: ${f.getClass
              .getSimpleName()}, accumulator type: ${acc.map(_.getClass.getSimpleName)}"
        )
    }.collect { case Some(f: WebSocketFrame) => f }
  else f
