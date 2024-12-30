package sttp.client4.compression

import java.io.UnsupportedEncodingException

/** Allows decompressing bodies, using the supported encoding. */
trait Decompressor[B] {
  def encoding: String
  def apply(body: B): B
}

object Decompressor {
  def decompressIfPossible[B](b: B, encoding: String, decompressors: List[Decompressor[B]]): B =
    decompressors.find(_.encoding.equalsIgnoreCase(encoding)) match {
      case Some(decompressor) => decompressor(b)
      case None               => throw new UnsupportedEncodingException(s"Unsupported encoding: $encoding")
    }
}
