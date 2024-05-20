package sttp.client4.ox.ws

import ox.*
import ox.channels.Channel
import ox.channels.ChannelClosed
import ox.channels.Sink
import ox.channels.Source
import sttp.client4.ws.SyncWebSocket
import sttp.ws.WebSocketFrame

import scala.util.control.NonFatal

  extension (ws: SyncWebSocket)(using Ox) def asSourceAndSink: (Source[WebSocketFrame], Sink[WebSocketFrame]) = 
    val srcChannel = Channel.bufferedDefault[WebSocketFrame]
    fork {
      repeatWhile {
        try ws.receive() match 
          case WebSocketFrame.Close(status, _) if status > 1001 => 
              srcChannel.error(new Exception(s"WebSocket closed with status $status"))
              false
          case _: WebSocketFrame.Close => 
              srcChannel.done()
              false
          case frame =>
              srcChannel.send(frame)
              true
        catch case NonFatal(err) => 
          srcChannel.error(err)          
          false
        }
    }
    val sinkChannel = Channel.bufferedDefault[WebSocketFrame]
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
    }
    (srcChannel, sinkChannel)
