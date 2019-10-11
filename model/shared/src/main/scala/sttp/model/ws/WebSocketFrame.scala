package sttp.model.ws

sealed trait WebSocketFrame

object WebSocketFrame {
  trait Incoming extends WebSocketFrame
  trait Data extends Incoming

  /**
    * A text frame with fragmentation or extension bits.
    *
    * @param payload a text fragment.
    * @param finalFragment flag indicating whether or not this is the final fragment
    * @param rsv optional extension bits
    */
  case class Text(payload: String, finalFragment: Boolean, rsv: Option[Int]) extends Data

  /**
    * A binary frame with fragmentation or extension bits.
    *
    * @param payload a binary payload
    * @param finalFragment flag indicating whether or not this is the last fragment
    * @param rsv optional extension bits
    *
    * @return an effect that will be completed once the frame will be actually written on the wire
    */
  case class Binary(payload: Array[Byte], finalFragment: Boolean, rsv: Option[Int]) extends Data

  case class Ping(payload: Array[Byte]) extends Incoming
  case class Pong(payload: Array[Byte]) extends Incoming
  case class Close(statusCode: Int, reasonText: String) extends WebSocketFrame

  def text(payload: String): Text = Text(payload, finalFragment = true, rsv = None)
  def binary(payload: Array[Byte]): Binary = Binary(payload, finalFragment = true, rsv = None)
  def ping: Ping = Ping(Array.emptyByteArray)
  def pong: Pong = Pong(Array.emptyByteArray)
  def close: Close = Close(1000, "normal closure")
}
