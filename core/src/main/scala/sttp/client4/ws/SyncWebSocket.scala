package sttp.client4.ws

import sttp.model.Headers
import sttp.shared.Identity
import sttp.ws.{WebSocket, WebSocketClosed, WebSocketFrame}

/** Allows interacting with a web socket. Interactions can happen:
  *
  *   - on the frame level, by sending and receiving raw [[WebSocketFrame]] s
  *   - using the provided `receive*` methods to obtain concatenated data frames, or string/byte payloads, and the
  *     `send*` method to send string/binary frames.
  *
  * The `send*` and `receive*` methods may result in a failed effect, with either one of [[sttp.ws.WebSocketException]]
  * exceptions, or a backend-specific exception. Specifically, they will fail with [[WebSocketClosed]] if the web socket
  * is closed.
  *
  * See the `either` and `eitherClose` method to lift web socket closed events to the value level.
  */
class SyncWebSocket(val delegate: WebSocket[Identity]) {

  /** Receive the next frame from the web socket. This can be a data frame, or a control frame including
    * [[WebSocketFrame.Close]]. After receiving a close frame, no further interactions with the web socket should
    * happen.
    *
    * However, not all implementations expose the close frame, and web sockets might also get closed without the proper
    * close frame exchange. In such cases, as well as when invoking `receive`/`send` after receiving a close frame, a
    * [[WebSocketClosed]] exception will be thrown.
    *
    * *Should be only called sequentially!* (from a single thread/fiber). Because web socket frames might be fragmented,
    * calling this method concurrently might result in threads/fibers receiving fragments of the same frame.
    */
  def receive(): WebSocketFrame = delegate.receive()

  /** Sends a web socket frame. Can be safely called from multiple threads.
    *
    * May result in an exception, in case of a network error, or if the socket is closed.
    */
  def send(f: WebSocketFrame, isContinuation: Boolean = false): Unit = delegate.send(f, isContinuation)

  def isOpen(): Boolean = delegate.isOpen()

  /** Receive a single data frame, ignoring others. The frame might be a fragment. Will throw [[WebSocketClosed]] if the
    * web socket is closed, or if a close frame is received.
    *
    * *Should be only called sequentially!* (from a single thread/fiber).
    *
    * @param pongOnPing
    *   Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveDataFrame(pongOnPing: Boolean = true): WebSocketFrame.Data[_] = delegate.receiveDataFrame(pongOnPing)

  /** Receive a single text data frame, ignoring others. The frame might be a fragment. To receive whole messages, use
    * [[receiveText]]. Will throw [[WebSocketClosed]] if the web socket is closed, or if a close frame is received.
    *
    * *Should be only called sequentially!* (from a single thread/fiber).
    *
    * @param pongOnPing
    *   Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveTextFrame(pongOnPing: Boolean = true): WebSocketFrame.Text = delegate.receiveTextFrame(pongOnPing)

  /** Receive a single binary data frame, ignoring others. The frame might be a fragment. To receive whole messages, use
    * [[receiveBinary]]. Will throw [[WebSocketClosed]] if the web socket is closed, or if a close frame is received.
    *
    * *Should be only called sequentially!* (from a single thread/fiber).
    *
    * @param pongOnPing
    *   Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveBinaryFrame(pongOnPing: Boolean = true): WebSocketFrame.Binary = delegate.receiveBinaryFrame(pongOnPing)

  /** Receive a single text message (which might come from multiple, fragmented frames). Ignores non-text frames and
    * returns combined results. Will throw [[WebSocketClosed]] if the web socket is closed, or if a close frame is
    * received.
    *
    * *Should be only called sequentially!* (from a single thread/fiber).
    *
    * @param pongOnPing
    *   Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveText(pongOnPing: Boolean = true): String = delegate.receiveText(pongOnPing)

  /** Receive a single binary message (which might come from multiple, fragmented frames). Ignores non-binary frames and
    * returns combined results. Will throw [[WebSocketClosed]] if the web socket is closed, or if a close frame is
    * received.
    *
    * *Should be only called sequentially!* (from a single thread/fiber).
    *
    * @param pongOnPing
    *   Should a [[WebSocketFrame.Pong]] be sent when a [[WebSocketFrame.Ping]] is received.
    */
  def receiveBinary(pongOnPing: Boolean): Array[Byte] = delegate.receiveBinary(pongOnPing)

  /** Extracts the received close frame (if available) as the left side of an either, or returns the original result on
    * the right.
    *
    * Will throw [[WebSocketClosed]] if the web socket is closed, but no close frame is available.
    *
    * @param f
    *   The effect describing web socket interactions.
    */
  def eitherClose[T](f: => T): Either[WebSocketFrame.Close, T] =
    try Right(f)
    catch {
      case WebSocketClosed(Some(close)) => Left(close)
    }

  /** Returns an effect computing a:
    *
    *   - `Left` if the web socket is closed - optionally with the received close frame (if available).
    *   - `Right` with the original result otherwise.
    *
    * Will never throw a [[WebSocketClosed]].
    *
    * @param f
    *   The effect describing web socket interactions.
    */
  def either[T](f: => T): Either[Option[WebSocketFrame.Close], T] =
    try Right(f)
    catch {
      case WebSocketClosed(close) => Left(close)
    }

  /** Sends a web socket frame with the given payload. Can be safely called from multiple threads.
    *
    * May result in an exception, in case of a network error, or if the socket is closed.
    */
  def sendText(payload: String): Unit = delegate.sendText(payload)

  /** Sends a web socket frame with the given payload. Can be safely called from multiple threads.
    *
    * May result in an exception, in case of a network error, or if the socket is closed.
    */
  def sendBinary(payload: Array[Byte]): Unit = delegate.sendBinary(payload)

  /** Idempotent when used sequentially. */
  def close(): Unit = delegate.close()

  def upgradeHeaders: Headers = delegate.upgradeHeaders
}
