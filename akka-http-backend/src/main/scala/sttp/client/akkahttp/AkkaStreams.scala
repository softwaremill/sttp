package sttp.client.akkahttp

import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import sttp.capabilities.Streams

trait AkkaStreams extends Streams[AkkaStreams] {
  override type BinaryStream = Source[ByteString, Any]
  override type Pipe[A, B] = Flow[A, B, Any]
}
object AkkaStreams extends AkkaStreams
