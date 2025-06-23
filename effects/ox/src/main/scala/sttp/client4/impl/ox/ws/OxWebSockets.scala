package sttp.client4.impl.ox.ws

import ox.*
import ox.channels.*
import sttp.client4.ws.SyncWebSocket
import sttp.ws.WebSocketFrame

import scala.util.control.NonFatal
import ox.flow.Flow

/** Using an open [[SyncWebSocket]], runs the given [[WebSocketFrame]]-processing `pipe` until the interaction is
  * complete, or an error occurs.
  *
  * The `pipe` is supplied with a flow of server responses (that is, frames received from the server). It should return
  * a flow of requests (that is, frames to be sent to the server).
  *
  * The pipe can perform arbitrary operations, although if the incoming frames are to be ignored, they should be drained
  * and the resulting flow merged with the requests (outgoing) flow. That way, server-side completion of the web socket
  * is guaranteed to be discovered.
  */
def runWebSocketPipe(ws: SyncWebSocket, concatenateFragmented: Boolean = true)(
    pipe: Flow[WebSocketFrame] => Flow[WebSocketFrame]
): Unit =
  supervised:
    releaseAfterScope(ws.close()) // an exception might be thrown when executing `pipe`; otherwise close is idempotent

    val pongsChannel = BufferCapacity.newChannel[WebSocketFrame]
    val responsesFlow = wsReceiveFlow(ws, pongsChannel, concatenateFragmented)
    val requestsFlow = pipe(responsesFlow)

    val requestsFlowWithPongs =
      requestsFlow.merge(Flow.fromSource(pongsChannel), propagateDoneLeft = true, propagateDoneRight = true)

    wsSendFlow(ws, requestsFlowWithPongs).runDrain()

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
  val responsesChannel = wsReceiveFlow(ws, requestsChannel, concatenateFragmented).runToChannel()

  fork {
    try wsSendFlow(ws, Flow.fromSource(requestsChannel)).runDrain()
    catch case NonFatal(e) => requestsChannel.errorOrClosed(e).discard
  }.discard

  (responsesChannel, requestsChannel)

final case class WebSocketClosedWithError(statusCode: Int, msg: String)
    extends Exception(s"WebSocket closed with status $statusCode: $msg")

//

private def optionallyConcatenateFrames(f: Flow[WebSocketFrame], doConcatenate: Boolean): Flow[WebSocketFrame] =
  if doConcatenate then
    type Accumulator = Option[Either[Array[Byte], String]]
    f.mapStateful(None: Accumulator) {
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

private def wsReceiveFlow(
    ws: SyncWebSocket,
    pongsSink: Sink[WebSocketFrame],
    concatenateFragmented: Boolean
): Flow[WebSocketFrame] =
  Flow
    .usingEmit[WebSocketFrame] { emit =>
      repeatWhile {
        ws.receive() match
          case frame: WebSocketFrame.Data[_]                      => emit(frame); true
          case WebSocketFrame.Close(status, msg) if status > 1001 => throw new WebSocketClosedWithError(status, msg)
          case _: WebSocketFrame.Close                            => false
          case ping: WebSocketFrame.Ping =>
            pongsSink.sendOrClosed(WebSocketFrame.Pong(ping.payload)).discard
            // Keep receiving even if pong couldn't be sent due to closed request channel. We want to process
            // whatever responses there are still coming from the server until it signals the end with a Close frome.
            true
          case _: WebSocketFrame.Pong =>
            // ignore pongs
            true
      }
    }
    .pipe(optionallyConcatenateFrames(_, concatenateFragmented))
    .onComplete(pongsSink.doneOrClosed().discard)

private def wsSendFlow(ws: SyncWebSocket, toSend: Flow[WebSocketFrame]): Flow[Unit] =
  toSend
    .takeWhile(
      {
        case _: WebSocketFrame.Close => false
        case _                       => true
      },
      includeFirstFailing = true
    )
    .map(frame => ws.send(frame))
    .onError { e =>
      // There's no proper "client error" status. Statuses 4000+ are available for custom cases
      try ws.send(WebSocketFrame.Close(4000, "Client error"))
      catch case NonFatal(e2) => e.addSuppressed(e2)
    }
    .onComplete(ws.close())
