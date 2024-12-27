package sttp.client4.compression

import sttp.client4._
import sttp.model.Encodings

import Compressor._
import java.nio.ByteBuffer
import java.util.zip.DeflaterInputStream
import java.util.zip.Deflater
import java.io.ByteArrayOutputStream

trait Compressor[R] {
  def encoding: String
  def apply(body: GenericRequestBody[R], encoding: String): GenericRequestBody[R]
}

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

private[client4] object Compressor {

  /** Compress the request body if needed, using the given compressors.
    * @return
    *   The optionally compressed body (if requested via request options), or the original body; and the content lenght,
    *   if known, of the uncompressed/compressed body.
    */
  def compressIfNeeded[T, R](
      request: GenericRequest[T, R],
      compressors: List[Compressor[R]]
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
    case FileBody(f, _)             => Some(f.length())
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
