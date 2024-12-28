package sttp.client4.compression

import sttp.client4._
import sttp.model.Encodings

import Compressor._
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import java.io.ByteArrayOutputStream

class GZipDefaultCompressor[R] extends Compressor[R] {
  val encoding: String = Encodings.Gzip

  def apply(body: GenericRequestBody[R], encoding: String): GenericRequestBody[R] =
    body match {
      case NoBody => NoBody
      case StringBody(s, encoding, defaultContentType) =>
        ByteArrayBody(byteArray(s.getBytes(encoding)), defaultContentType)
      case ByteArrayBody(b, defaultContentType) => ByteArrayBody(byteArray(b), defaultContentType)
      case ByteBufferBody(b, defaultContentType) =>
        ByteArrayBody(byteArray(byteBufferToArray(b)), defaultContentType)
      case InputStreamBody(b, defaultContentType) =>
        InputStreamBody(new GZIPCompressingInputStream(b), defaultContentType)
      case StreamBody(b) => streamsNotSupported
      case FileBody(f, defaultContentType) =>
        InputStreamBody(new GZIPCompressingInputStream(f.openStream()), defaultContentType)
      case MultipartStreamBody(parts) => compressingMultipartBodiesNotSupported
      case BasicMultipartBody(parts)  => compressingMultipartBodiesNotSupported
    }

  private def byteArray(bytes: Array[Byte]): Array[Byte] = {
    val bos = new java.io.ByteArrayOutputStream()
    val gzip = new java.util.zip.GZIPOutputStream(bos)
    gzip.write(bytes)
    gzip.close()
    bos.toByteArray()
  }
}

class DeflateDefaultCompressor[R] extends Compressor[R] {
  val encoding: String = Encodings.Deflate

  def apply(body: GenericRequestBody[R], encoding: String): GenericRequestBody[R] =
    body match {
      case NoBody => NoBody
      case StringBody(s, encoding, defaultContentType) =>
        ByteArrayBody(byteArray(s.getBytes(encoding)), defaultContentType)
      case ByteArrayBody(b, defaultContentType) => ByteArrayBody(byteArray(b), defaultContentType)
      case ByteBufferBody(b, defaultContentType) =>
        ByteArrayBody(byteArray(byteBufferToArray(b)), defaultContentType)
      case InputStreamBody(b, defaultContentType) =>
        InputStreamBody(new DeflaterInputStream(b), defaultContentType)
      case StreamBody(b) => streamsNotSupported
      case FileBody(f, defaultContentType) =>
        InputStreamBody(new DeflaterInputStream(f.openStream()), defaultContentType)
      case MultipartStreamBody(parts) => compressingMultipartBodiesNotSupported
      case BasicMultipartBody(parts)  => compressingMultipartBodiesNotSupported
    }

  private def byteArray(bytes: Array[Byte]): Array[Byte] = {
    val deflater = new Deflater()
    try {
      deflater.setInput(bytes)
      deflater.finish()
      val byteArrayOutputStream = new ByteArrayOutputStream()
      val readBuffer = new Array[Byte](1024)

      while (!deflater.finished()) {
        val readCount = deflater.deflate(readBuffer)
        if (readCount > 0) {
          byteArrayOutputStream.write(readBuffer, 0, readCount)
        }
      }

      byteArrayOutputStream.toByteArray
    } finally deflater.end()
  }
}
