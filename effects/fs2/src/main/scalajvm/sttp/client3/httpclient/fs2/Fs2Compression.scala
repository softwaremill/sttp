package sttp.client3.httpclient.fs2

import fs2.{Pipe, Pull}
import fs2.compression.{Compression, InflateParams, ZLibParams}

object Fs2Compression {

  def inflateCheckHeader[F[_]: Compression]: Pipe[F, Byte, Byte] = stream =>
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
