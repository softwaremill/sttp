package sttp.client.akkahttp

import akka.stream.scaladsl.Source
import akka.util.ByteString
import sttp.client.Streams

trait AkkaStreams extends Streams[AkkaStreams] {
  override type BinaryStream = Source[ByteString, Any]
}
object AkkaStreams extends AkkaStreams
