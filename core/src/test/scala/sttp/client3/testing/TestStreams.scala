package sttp.client3.testing

import sttp.capabilities.Streams

trait TestStreams extends Streams[TestStreams] {
  override type BinaryStream = List[Byte]
  override type Pipe[A, B] = A => B
}

object TestStreams extends TestStreams
