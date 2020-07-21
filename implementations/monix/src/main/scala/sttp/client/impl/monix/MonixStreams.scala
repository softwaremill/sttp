package sttp.client.impl.monix

import java.nio.ByteBuffer

import monix.reactive.Observable
import sttp.client.Streams

trait MonixStreams extends Streams[MonixStreams] {
  override type BinaryStream = Observable[ByteBuffer]
}
object MonixStreams extends MonixStreams
