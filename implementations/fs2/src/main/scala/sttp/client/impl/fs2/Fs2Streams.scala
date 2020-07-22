package sttp.client.impl.fs2

import fs2.Stream
import sttp.client.Streams

trait Fs2Streams[F[_]] extends Streams[Fs2Streams[F]] {
  override type BinaryStream = Stream[F, Byte]
}
