package sttp.client4.impl.fs2

import fs2.compression.Compression
import fs2.Stream
import sttp.client4.compression.Decompressor
import sttp.model.Encodings
import fs2.Pipe
import fs2.Pull
import fs2.compression.ZLibParams
import fs2.compression.InflateParams

class GZipFs2Decompressor[F[_]: Compression] extends Decompressor[Stream[F, Byte]] {
  override val encoding: String = Encodings.Gzip
  override def apply(body: Stream[F, Byte]): Stream[F, Byte] =
    body.through(fs2.compression.Compression[F].gunzip()).flatMap(_.content)
}

class DeflateFs2Decompressor[F[_]: Compression] extends Decompressor[Stream[F, Byte]] {
  override val encoding: String = Encodings.Deflate
  override def apply(body: Stream[F, Byte]): Stream[F, Byte] = body.through(inflateCheckHeader[F])

  private def inflateCheckHeader[F[_]: Compression]: Pipe[F, Byte, Byte] = stream =>
    stream.pull.uncons1
      .flatMap {
        case None                 => Pull.done
        case Some((byte, stream)) => Pull.output1((byte, stream))
      }
      .stream
      .flatMap { case (byte, stream) =>
        val header = if ((byte & 0x0f) == 0x08) ZLibParams.Header.ZLIB else ZLibParams.Header.GZIP
        val params = InflateParams(header = header)
        stream.cons1(byte).through(fs2.compression.Compression[F].inflate(params))
      }
}
