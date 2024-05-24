package sttp.client4.impl.ox.ws

import ox.*
import ox.channels.*
import sttp.client4.ws.SyncWebSocket
import sttp.ws.WebSocketFrame

import scala.util.control.NonFatal

/** Converts a [[SyncWebSocket]] into a pair of `Source` of server responses and a `Sink` for client requests. The
  * `Source` starts receiving frames immediately, its internal buffer size can be adjusted with an implicit
  * [[ox.channels.StageCapacity]]. 
  * Make sure that the `Source` is contiunually read. This will guarantee that server-side Close signal is received and handled.
  * If you don't want to process frames from the server, you can at least handle it with a `fork { source.drain() }`.
  *
  * You don't need to manually call `ws.close()` when using this approach, this will be
  * handled automatically underneath, according to following rules:
  *   - If the request `Sink` fails with an error, the `Source` is automatically completed, sending a `Close` frame to
  *     the server if needed.
  *   - If the request `Sink` completes without an error, a `Close` frame is sent, and the response `Sink` keeps
  *     receiving responses until the server closes communication.
  *   - If the response `Source` is completed (either due to completion or an error), the request Sink is completed,
  *     right after sending all outstanding buffered frames.
  *
  * @param ws
  *   a `SyncWebSocket` where the underlying `Sink` will send requests, and where the `Source` will pull responses from.
  * @param concatenateFragmented
  *   whether fragmented frames from the server should be concatenated into a single frame (true by default).
  */
def asSourceAndSink(ws: SyncWebSocket, concatenateFragmented: Boolean = true)(using
    Ox,
    StageCapacity
): (Source[WebSocketFrame], Sink[WebSocketFrame]) =
  val requestsChannel = StageCapacity.newChannel[WebSocketFrame]
  val responsesChannel = StageCapacity.newChannel[WebSocketFrame]
  fork {
    try
      repeatWhile {
        ws.receive() match
          case frame: WebSocketFrame.Data[_] =>
            responsesChannel.sendOrClosed(frame) match
              case _: ChannelClosed => false
              case _                => true
          case WebSocketFrame.Close(status, msg) if status > 1001 =>
            responsesChannel.errorOrClosed(new WebSocketClosedWithError(status, msg)).discard
            false
          case _: WebSocketFrame.Close =>
            responsesChannel.doneOrClosed().discard
            false
          case ping: WebSocketFrame.Ping =>
            requestsChannel.sendOrClosed(WebSocketFrame.Pong(ping.payload)).discard
            true
          case _: WebSocketFrame.Pong =>
            // ignore pongs
            true
      }
    catch
      case NonFatal(err) =>
        responsesChannel.errorOrClosed(err).discard
    finally requestsChannel.doneOrClosed().discard
  }.discard

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
            responsesChannel.doneOrClosed().discard
            false
      }
    catch
      case NonFatal(err) =>
        // If responses are closed, server finished the communication and we can ignore that send() failed
        if (!responsesChannel.isClosedForReceive) requestsChannel.errorOrClosed(err).discard
  }.discard

  (optionallyConcatenateFrames(responsesChannel, concatenateFragmented), requestsChannel)

final case class WebSocketClosedWithError(statusCode: Int, msg: String)
    extends Exception(s"WebSocket closed with status $statusCode: $msg")

private def optionallyConcatenateFrames(s: Source[WebSocketFrame], doConcatenate: Boolean)(using
    Ox
): Source[WebSocketFrame] =
  if doConcatenate then
    type Accumulator = Option[Either[Array[Byte], String]]
    s.mapStateful(() => None: Accumulator) {
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
    }.collectAsView { case Some(f: WebSocketFrame) => f }
  else s
