package sttp.client.impl.zio

import zio.stream.Stream
import sttp.client.Streams

trait ZioStreams extends Streams[ZioStreams] {
  override type BinaryStream = Stream[Throwable, Byte]
}
object ZioStreams extends ZioStreams
