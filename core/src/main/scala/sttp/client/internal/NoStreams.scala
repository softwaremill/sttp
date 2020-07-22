package sttp.client.internal

import sttp.client.Streams

trait NoStreams extends Streams[Nothing] {
  override type BinaryStream = Nothing
}
object NoStreams extends NoStreams
