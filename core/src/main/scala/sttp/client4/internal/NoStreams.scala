package sttp.client4.internal

import sttp.capabilities.Streams

private[client4] trait NoStreams extends Streams[Nothing] {
  override type BinaryStream = Nothing
  override type Pipe[A, B] = Nothing
}
private[client4] object NoStreams extends NoStreams
