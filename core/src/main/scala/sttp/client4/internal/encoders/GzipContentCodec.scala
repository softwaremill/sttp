package sttp.client4.internal.encoders

import sttp.client4.internal.ContentEncoding
import sttp.client4.internal.ContentEncoding.Gzip
import sttp.client4.internal.encoders.EncoderError.EncodingFailure

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.util.Using

class GzipContentCodec extends AbstractContentCodec[Gzip] {

  override def encode(bytes: Array[Byte]): Either[EncodingFailure, Array[Byte]] = {
    Using(new ByteArrayOutputStream){ baos =>
      Using(new GZIPOutputStream(baos)){ gzos =>
        gzos.write(bytes)
        gzos.finish()
        baos.toByteArray
      }
    }.flatMap(identity).toEither.left.map(ex => EncodingFailure(encoding, ex.getMessage))
  }

  override def decode(bytes: Array[Byte]): Either[EncodingFailure, Array[Byte]] = {
    Using(new GZIPInputStream(new ByteArrayInputStream(bytes))) { b =>
      b.readAllBytes()
    }.toEither.left.map(ex => EncodingFailure(encoding, ex.getMessage))
  }

  override def encoding: Gzip = ContentEncoding.gzip

}
