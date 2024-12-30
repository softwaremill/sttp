package sttp.client4.compression

import sttp.client4._
import java.nio.ByteBuffer

/** Allows compressing bodies, using the supported encoding. The compressed bodies might use `R` capabilities (e.g.
  * streaming).
  */
trait Compressor[-R] {
  def encoding: String
  def apply[R2 <: R](body: GenericRequestBody[R2]): GenericRequestBody[R]
}

object Compressor extends CompressorExtensions {

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
          case Some(compressor) => compressor(request.body)
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
