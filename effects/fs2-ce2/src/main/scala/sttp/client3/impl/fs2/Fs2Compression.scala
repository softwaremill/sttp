package sttp.client3.impl.fs2

import cats.effect.Sync
import fs2.{Pipe, Pull}

object Fs2Compression {

  def inflateCheckHeader[F[_]: Sync]: Pipe[F, Byte, Byte] = stream =>
    stream.pull.uncons1
      .flatMap {
        case None                 => Pull.done
        case Some((byte, stream)) => Pull.output1((byte, stream))
      }
      .stream
      .flatMap { case (byte, stream) =>
        val wrapped = (byte & 0x0f) == 0x08
        stream.cons1(byte).through(fs2.compression.inflate(nowrap = !wrapped))
      }
}
