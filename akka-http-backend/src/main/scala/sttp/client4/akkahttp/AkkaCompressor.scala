package sttp.client4.akkahttp

import akka.util.ByteString
import akka.stream.scaladsl.Compression
import sttp.capabilities.akka.AkkaStreams
import akka.stream.scaladsl.Source
import sttp.client4._
import sttp.client4.compression.DeflateDefaultCompressor
import sttp.client4.compression.GZipDefaultCompressor
import sttp.client4.compression.Compressor
import akka.stream.scaladsl.StreamConverters
import akka.stream.scaladsl.FileIO

trait AkkaCompressor[R <: AkkaStreams] extends Compressor[R] {
  override abstract def apply(body: GenericRequestBody[R], encoding: String): GenericRequestBody[R] =
    body match {
      case InputStreamBody(b, _) => StreamBody(AkkaStreams)(compressStream(StreamConverters.fromInputStream(() => b)))
      case StreamBody(b)         => StreamBody(AkkaStreams)(compressStream(b.asInstanceOf[Source[ByteString, Any]]))
      case FileBody(f, _)        => StreamBody(AkkaStreams)(compressStream(FileIO.fromPath(f.toPath)))
      case _                     => super.apply(body, encoding)
    }

  def compressStream(stream: Source[ByteString, Any]): Source[ByteString, Any]
}

class GZipAkkaCompressor[R <: AkkaStreams] extends GZipDefaultCompressor[R] with AkkaCompressor[R] {
  def compressStream(stream: Source[ByteString, Any]): Source[ByteString, Any] = stream.via(Compression.gzip)
}

class DeflateAkkaCompressor[R <: AkkaStreams] extends DeflateDefaultCompressor[R] with AkkaCompressor[R] {
  def compressStream(stream: Source[ByteString, Any]): Source[ByteString, Any] = stream.via(Compression.deflate)
}
