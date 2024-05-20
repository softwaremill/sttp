package sttp.client4.ox.ws

import ox.*
import ox.channels.ChannelClosed
import ox.channels.Sink
import ox.channels.Source
import ox.channels.StageCapacity
import sttp.client4.ws.SyncWebSocket
import sttp.ws.WebSocketFrame

import scala.util.control.NonFatal

  def asSource(ws: SyncWebSocket, concatenateFragmented: Boolean = true, pongOnPing: Boolean = true)(using Ox, StageCapacity): Source[WebSocketFrame] =
    val srcChannel = StageCapacity.newChannel[WebSocketFrame]
    fork {
      repeatWhile {
        try
          ws.receive() match
            case frame: WebSocketFrame.Data[_] =>
              srcChannel.send(frame)
              true
            case WebSocketFrame.Close(status, msg) if status > 1001 =>
              srcChannel.error(new WebSocketClosedWithError(status, msg))
              false
            case _: WebSocketFrame.Close =>
              srcChannel.done()
              false
            case ping: WebSocketFrame.Ping =>
              if pongOnPing then ws.send(WebSocketFrame.Pong(ping.payload))            
              true
            case _: WebSocketFrame.Pong =>
              // ignore pongs
              true
        catch
          case NonFatal(err) =>
            srcChannel.error(err)
            false
      }
    }.discard
    optionallyConcatenateFrames(srcChannel, concatenateFragmented)

  def asSink(ws: SyncWebSocket)(using Ox, StageCapacity): Sink[WebSocketFrame] =
    val sinkChannel = StageCapacity.newChannel[WebSocketFrame]
    fork {
      try
        repeatWhile {
          sinkChannel.receiveOrClosed() match
            case closeFrame: WebSocketFrame.Close =>
              ws.send(closeFrame) // TODO should we just let 'send' throw exceptions?
              false
            case frame: WebSocketFrame =>
              ws.send(frame)
              true
            case ChannelClosed.Done =>
              ws.send(WebSocketFrame.close)
              false
            case ChannelClosed.Error(err) =>
              // There's no proper "client error" status. Statuses 4000+ are available for custom cases
              ws.send(WebSocketFrame.Close(4000, "Client error")) // TODO should we bother the server with client error?
              false
        }
      finally uninterruptible(ws.close())
    }.discard
    sinkChannel

  def asSourceAndSink(ws: SyncWebSocket, concatenateFragmented: Boolean = true, pongOnPing: Boolean = true)(using Ox, StageCapacity): (Source[WebSocketFrame], Sink[WebSocketFrame]) =
    (asSource(ws, concatenateFragmented, pongOnPing), asSink(ws))

final case class WebSocketClosedWithError(statusCode: Int, msg: String) extends Exception(s"WebSocket closed with status $statusCode: $msg")

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
        throw new IllegalStateException(s"Unexpected WebSocket frame received during concatenation. Frame received: ${f.getClass.getSimpleName()}, accumulator type: ${acc.map(_.getClass.getSimpleName)}")
    }.collectAsView { case Some(f: WebSocketFrame) => f }
  else s
