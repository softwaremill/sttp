package sttp.client4.internal.encoders

import sttp.client4.internal.ContentEncoding
import sttp.client4.internal.ContentEncoding.{Deflate, Gzip}
import sttp.client4.internal.encoders.EncoderError.UnsupportedEncoding
import sttp.client4.{BasicBodyPart, ByteArrayBody, ByteBufferBody, FileBody, InputStreamBody, StringBody}
import sttp.model.MediaType

import scala.annotation.tailrec

trait ContentCodec[C <: ContentEncoding] {

  type BodyWithLength = (BasicBodyPart, Int)

  def encode(body: BasicBodyPart): Either[EncoderError, BodyWithLength]

  def decode(body: BasicBodyPart): Either[EncoderError, BodyWithLength]

  def encoding: C

}

abstract class AbstractContentCodec[C <: ContentEncoding] extends ContentCodec[C] {

  override def encode(body: BasicBodyPart): Either[EncoderError, BodyWithLength] =
    body match {
      case StringBody(s, encoding, ct) => encode(s.getBytes(encoding), ct)
      case ByteArrayBody(b, ct)        => encode(b, ct)
      case ByteBufferBody(b, ct)       => encode(b.array(), ct)
      case InputStreamBody(b, ct)      => encode(b.readAllBytes(), ct)
      case FileBody(f, ct)             => encode(f.readAsByteArray, ct)
    }

  private def encode(bytes: Array[Byte], ct: MediaType): Either[EncoderError, BodyWithLength] =
    encode(bytes).map(r => ByteArrayBody(r, ct) -> r.length)

  override def decode(body: BasicBodyPart): Either[EncoderError, BodyWithLength] = body match {
    case StringBody(s, encoding, ct) => decode(s.getBytes(encoding), ct)
    case ByteArrayBody(b, ct)        => decode(b, ct)
    case ByteBufferBody(b, ct)       => decode(b.array(), ct)
    case InputStreamBody(b, ct)      => decode(b.readAllBytes(), ct)
    case FileBody(f, ct)             => decode(f.readAsByteArray, ct)
  }

  private def decode(bytes: Array[Byte], ct: MediaType): Either[EncoderError, BodyWithLength] =
    decode(bytes).map(r => ByteArrayBody(r, ct) -> r.length)

  def encode(bytes: Array[Byte]): Either[EncoderError, Array[Byte]]
  def decode(bytes: Array[Byte]): Either[EncoderError, Array[Byte]]
}

object ContentCodec {

  private val gzipCodec = new GzipContentCodec

  private val deflateCodec = new DeflateContentCodec

  def encode(b: BasicBodyPart, codec: List[ContentEncoding]): Either[EncoderError, (BasicBodyPart, Int)] =
    foldLeftInEither(codec, b -> 0) { case ((l, _), r) =>
      r match {
        case _: Gzip    => gzipCodec.encode(l)
        case _: Deflate => deflateCodec.encode(l)
        case e          => Left(UnsupportedEncoding(e))
      }
    }

  @tailrec
  private def foldLeftInEither[T, R, E](elems: List[T], zero: R)(f: (R, T) => Either[E, R]): Either[E, R] =
    elems match {
      case Nil => Right[E, R](zero)
      case head :: tail =>
        f(zero, head) match {
          case l: Left[E, R] => l
          case Right(v)      => foldLeftInEither(tail, v)(f)
        }
    }

}
