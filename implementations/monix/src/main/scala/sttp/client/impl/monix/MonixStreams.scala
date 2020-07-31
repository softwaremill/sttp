package sttp.client.impl.monix

import java.nio.ByteBuffer

import monix.reactive.Observable
import sttp.client.Streams

trait MonixStreams extends Streams[MonixStreams] {
  override type BinaryStream = Observable[ByteBuffer]
  override type Pipe[A, B] = Observable[A] => Observable[B]
}
object MonixStreams extends MonixStreams
