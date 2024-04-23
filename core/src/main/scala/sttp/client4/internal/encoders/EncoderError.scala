package sttp.client4.internal.encoders

import sttp.client4.internal.ContentEncoding

import scala.util.control.NoStackTrace

sealed trait EncoderError extends Exception with NoStackTrace {
  def reason: String
}

object EncoderError {
  case class UnsupportedEncoding(encoding: ContentEncoding) extends EncoderError {
    override def reason: String = s"${encoding.name} is unsupported with this body"
  }

  case class EncodingFailure(encoding: ContentEncoding, msg: String) extends EncoderError {

    override def reason: String = s"Can`t encode $encoding for body $msg"
  }
}
