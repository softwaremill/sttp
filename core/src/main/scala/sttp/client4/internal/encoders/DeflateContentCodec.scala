package sttp.client4.internal.encoders

import sttp.client4.internal.ContentEncoding
import sttp.client4.internal.ContentEncoding.Deflate

import java.io.ByteArrayOutputStream
import java.util.zip.{Deflater, Inflater}
import scala.util.{Try, Using}

class DeflateContentCodec extends AbstractContentCodec[Deflate] {

  override def encode(bytes: Array[Byte]): Either[EncoderError, Array[Byte]] =
    Try {
      val deflater: Deflater = new Deflater()
      deflater.setInput(bytes)
      deflater.finish()
      val compressedData = new Array[Byte](bytes.length * 2)
      val count: Int = deflater.deflate(compressedData)
      compressedData.take(count)
    }.toEither.left.map(ex => EncoderError.EncodingFailure(encoding, ex.getMessage))

  override def decode(bytes: Array[Byte]): Either[EncoderError, Array[Byte]] =
    Using(new ByteArrayOutputStream()){ bos =>
      val buf = new Array[Byte](1024)
      val decompresser = new Inflater()
      decompresser.setInput(bytes, 0, bytes.length)
      while (!decompresser.finished) {
        val resultLength = decompresser.inflate(buf)
        bos.write(buf, 0, resultLength)
      }
      decompresser.end()
      bos.toByteArray
    }.toEither.left.map(ex => EncoderError.EncodingFailure(encoding, ex.getMessage))

  override def encoding: Deflate = ContentEncoding.deflate
}
