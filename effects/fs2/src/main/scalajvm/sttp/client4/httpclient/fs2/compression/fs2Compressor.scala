package sttp.client4.httpclient.fs2.compression

import sttp.client4._
import sttp.client4.internal.compression._
import sttp.client4.GenericRequestBody
import fs2._
import fs2.compression.Compression
import cats.syntax.all._
import fs2.io.file.Files
import cats.effect.Sync
import sttp.capabilities.fs2.Fs2Streams
import fs2.compression.DeflateParams

trait Fs2Compressor[F[_], R <: Fs2Streams[F]] extends Compressor[R] {
  protected val fSync: Sync[F]
  protected val fFiles: Files[F]

  override abstract def apply(body: GenericRequestBody[R], encoding: String): GenericRequestBody[R] =
    body match {
      case InputStreamBody(b, defaultContentType) =>
        StreamBody(Fs2Streams[F])(compressStream(fs2.io.readInputStream(b.pure[F](fSync), 1024)(fSync)))
      case StreamBody(b) => StreamBody(Fs2Streams[F])(compressStream(b.asInstanceOf[fs2.Stream[F, Byte]]))
      case FileBody(f, defaultContentType) =>
        StreamBody(Fs2Streams[F])(compressStream(Files[F](fFiles).readAll(f.toPath, 1024)))
      case _ => super.apply(body, encoding)
    }

  def compressStream(stream: fs2.Stream[F, Byte]): fs2.Stream[F, Byte]
}

class GZipFs2Compressor[F[_]: Compression: Sync: Files, R <: Fs2Streams[F]]
    extends GZipDefaultCompressor[R]
    with Fs2Compressor[F, R] {

  override protected val fSync: Sync[F] = implicitly
  override protected val fFiles: Files[F] = implicitly

  def compressStream(stream: Stream[F, Byte]): Stream[F, Byte] = stream.through(fs2.compression.Compression[F].gzip())
}

class DeflateFs2Compressor[F[_]: Compression: Sync: Files, R <: Fs2Streams[F]]
    extends DeflateDefaultCompressor[R]
    with Fs2Compressor[F, R] {
  override protected val fSync: Sync[F] = implicitly
  override protected val fFiles: Files[F] = implicitly

  def compressStream(stream: Stream[F, Byte]): Stream[F, Byte] =
    stream.through(fs2.compression.Compression[F].deflate(DeflateParams()))
}
