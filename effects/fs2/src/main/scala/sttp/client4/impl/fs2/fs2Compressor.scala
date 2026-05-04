package sttp.client4.impl.fs2

import sttp.client4._
import sttp.client4.GenericRequestBody
import fs2._
import fs2.compression.Compression
import cats.syntax.all._
import cats.effect.Sync
import sttp.capabilities.fs2.Fs2Streams
import fs2.compression.DeflateParams
import sttp.client4.compression.Compressor
import sttp.model.Encodings

trait Fs2Compressor[F[_], R <: Fs2Streams[F]] extends Compressor[R] {
  protected val fSync: Sync[F]

  override def apply[R2 <: R](body: GenericRequestBody[R2]): GenericRequestBody[R] =
    body match {
      case NoBody                     => NoBody
      case StringBody(s, encoding, _) =>
        StreamBody(Fs2Streams[F])(compressStream(Stream.chunk(Chunk.array(s.getBytes(encoding)))))
      case ByteArrayBody(b, _) =>
        StreamBody(Fs2Streams[F])(compressStream(Stream.chunk(Chunk.array(b))))
      case ByteBufferBody(b, _) =>
        val bytes =
          if (b.hasArray()) b.array()
          else {
            val arr = new Array[Byte](b.remaining())
            b.get(arr)
            arr
          }
        StreamBody(Fs2Streams[F])(compressStream(Stream.chunk(Chunk.array(bytes))))
      case InputStreamBody(b, _) =>
        compressInputStreamBody(b)
      case StreamBody(b) =>
        StreamBody(Fs2Streams[F])(compressStream(b.asInstanceOf[Stream[F, Byte]]))
      case fb @ FileBody(_, _) =>
        compressFileBody(fb)
      case MultipartStreamBody(_) | BasicMultipartBody(_) =>
        throw new IllegalArgumentException("Multipart bodies cannot be compressed")
    }

  protected def compressInputStreamBody(b: java.io.InputStream): GenericRequestBody[R] =
    throw new UnsupportedOperationException("InputStream compression is not supported on this platform")

  protected def compressFileBody(f: FileBody): GenericRequestBody[R] =
    throw new UnsupportedOperationException("File compression is not supported on this platform")

  def compressStream(stream: Stream[F, Byte]): Stream[F, Byte]
}

class GZipFs2Compressor[F[_]: Compression: Sync, R <: Fs2Streams[F]] extends Fs2Compressor[F, R] {

  override protected val fSync: Sync[F] = implicitly
  override val encoding: String = Encodings.Gzip

  def compressStream(stream: Stream[F, Byte]): Stream[F, Byte] =
    stream.through(fs2.compression.Compression[F].gzip())
}

class DeflateFs2Compressor[F[_]: Compression: Sync, R <: Fs2Streams[F]] extends Fs2Compressor[F, R] {

  override protected val fSync: Sync[F] = implicitly
  override val encoding: String = Encodings.Deflate

  def compressStream(stream: Stream[F, Byte]): Stream[F, Byte] =
    stream.through(fs2.compression.Compression[F].deflate(DeflateParams()))
}
