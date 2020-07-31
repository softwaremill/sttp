package sttp.client.impl.fs2

import fs2.Stream
import sttp.client.Streams

trait Fs2Streams[F[_]] extends Streams[Fs2Streams[F]] {
  override type BinaryStream = Stream[F, Byte]
  override type Pipe[A, B] = fs2.Pipe[F, A, B]
}
object Fs2Streams {
  def apply[F[_]]: Fs2Streams[F] = new Fs2Streams[F] {}
}
