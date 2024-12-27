package sttp.client4.internal.compression

import sttp.client4._
import sttp.model.Encodings

import Compressor._
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.zip.DeflaterInputStream
import java.util.zip.Deflater
import java.io.ByteArrayOutputStream

private[client4] trait Compressor {
  def encoding: String
  def apply[R](body: GenericRequestBody[R], encoding: String): GenericRequestBody[R]
}

private[client4] object GZipDefaultCompressor extends Compressor {
  val encoding: String = Encodings.Gzip

  def apply[R](body: GenericRequestBody[R], encoding: String): GenericRequestBody[R] =
    body match {
      case NoBody => NoBody
      case StringBody(s, encoding, defaultContentType) =>
        ByteArrayBody(byteArray(s.getBytes(encoding)), defaultContentType)
      case ByteArrayBody(b, defaultContentType) => ByteArrayBody(byteArray(b), defaultContentType)
      case ByteBufferBody(b, defaultContentType) =>
        ByteArrayBody(byteArray(byteBufferToArray(b)), defaultContentType)
      case InputStreamBody(b, defaultContentType) =>
        InputStreamBody(GZIPCompressingInputStream(b), defaultContentType)
      case StreamBody(b) => streamsNotSupported
      case FileBody(f, defaultContentType) =>
        InputStreamBody(GZIPCompressingInputStream(new FileInputStream(f.toFile)), defaultContentType)
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

private[client4] object DeflateDefaultCompressor extends Compressor {
  val encoding: String = Encodings.Deflate

  def apply[R](body: GenericRequestBody[R], encoding: String): GenericRequestBody[R] =
    body match {
      case NoBody => NoBody
      case StringBody(s, encoding, defaultContentType) =>
        ByteArrayBody(byteArray(s.getBytes(encoding)), defaultContentType)
      case ByteArrayBody(b, defaultContentType) => ByteArrayBody(byteArray(b), defaultContentType)
      case ByteBufferBody(b, defaultContentType) =>
        ByteArrayBody(byteArray(byteBufferToArray(b)), defaultContentType)
      case InputStreamBody(b, defaultContentType) =>
        InputStreamBody(DeflaterInputStream(b), defaultContentType)
      case StreamBody(b) => streamsNotSupported
      case FileBody(f, defaultContentType) =>
        InputStreamBody(DeflaterInputStream(new FileInputStream(f.toFile)), defaultContentType)
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

private[client4] object Compressor {

  /** Compress the request body if needed, using the given compressors.
    * @return
    *   The optionally compressed body (if requested via request options), or the original body; and the content lenght,
    *   if known, of the uncompressed/compressed body.
    */
  def compressIfNeeded[T, R](
      request: GenericRequest[T, R],
      compressors: List[Compressor]
  ): (GenericRequestBody[R], Option[Long]) =
    request.options.compressRequestBody match {
      case Some(encoding) =>
        val compressedBody = compressors.find(_.encoding.equalsIgnoreCase(encoding)) match {
          case Some(compressor) => compressor(request.body, encoding)
          case None             => throw new IllegalArgumentException(s"Unsupported encoding: $encoding")
        }

        val contentLength = calculateContentLength(compressedBody)
        (compressedBody, contentLength)

      case None => (request.body, request.contentLength)
    }

  private def calculateContentLength[R](body: GenericRequestBody[R]): Option[Long] = body match {
    case NoBody                     => None
    case StringBody(b, e, _)        => Some(b.getBytes(e).length.toLong)
    case ByteArrayBody(b, _)        => Some(b.length.toLong)
    case ByteBufferBody(b, _)       => None
    case InputStreamBody(b, _)      => None
    case FileBody(f, _)             => Some(f.toFile.length())
    case StreamBody(_)              => None
    case MultipartStreamBody(parts) => None
    case BasicMultipartBody(parts)  => None
  }

  private[compression] def compressingMultipartBodiesNotSupported: Nothing =
    throw new IllegalArgumentException("Multipart bodies cannot be compressed")

  private[compression] def streamsNotSupported: Nothing =
    throw new IllegalArgumentException("Streams are not supported")

  private[compression] def byteBufferToArray(inputBuffer: ByteBuffer): Array[Byte] =
    if (inputBuffer.hasArray()) {
      inputBuffer.array()
    } else {
      val inputBytes = new Array[Byte](inputBuffer.remaining())
      inputBuffer.get(inputBytes)
      inputBytes
    }
}
