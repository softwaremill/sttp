package sttp.client.impl.zio

import zio.stream.{Stream, ZStream}
import sttp.client.Streams
import zio.blocking.Blocking

trait ZioStreams extends Streams[ZioStreams] {
  override type BinaryStream = Stream[Throwable, Byte]
}
object ZioStreams extends ZioStreams

trait BlockingZioStreams extends Streams[BlockingZioStreams] {
  override type BinaryStream = ZStream[Blocking, Throwable, Byte]
}
object BlockingZioStreams extends BlockingZioStreams
