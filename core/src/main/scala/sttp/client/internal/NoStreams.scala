package sttp.client.internal

import sttp.client.Streams

trait NoStreams extends Streams[Nothing] {
  override type BinaryStream = Nothing
  override type Pipe[A, B] = Nothing
}
object NoStreams extends NoStreams
