package sttp.client4.pekkohttp

import org.apache.pekko.util.ByteString
import org.apache.pekko.stream.scaladsl.Compression
import sttp.capabilities.pekko.PekkoStreams
import org.apache.pekko.stream.scaladsl.Source
import sttp.client4._
import sttp.client4.compression.DeflateDefaultCompressor
import sttp.client4.compression.GZipDefaultCompressor
import sttp.client4.compression.Compressor
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.apache.pekko.stream.scaladsl.FileIO

trait PekkoCompressor extends Compressor[PekkoStreams] {
  override abstract def apply[R2 <: PekkoStreams](body: GenericRequestBody[R2]): GenericRequestBody[PekkoStreams] =
    body match {
      case InputStreamBody(b, _) => StreamBody(PekkoStreams)(compressStream(StreamConverters.fromInputStream(() => b)))
      case StreamBody(b)         => StreamBody(PekkoStreams)(compressStream(b.asInstanceOf[Source[ByteString, Any]]))
      case FileBody(f, _)        => StreamBody(PekkoStreams)(compressStream(FileIO.fromPath(f.toPath)))
      case _                     => super.apply(body)
    }

  def compressStream(stream: Source[ByteString, Any]): Source[ByteString, Any]
}

object GZipPekkoCompressor extends GZipDefaultCompressor[PekkoStreams] with PekkoCompressor {
  def compressStream(stream: Source[ByteString, Any]): Source[ByteString, Any] = stream.via(Compression.gzip)
}

object DeflatePekkoCompressor extends DeflateDefaultCompressor[PekkoStreams] with PekkoCompressor {
  def compressStream(stream: Source[ByteString, Any]): Source[ByteString, Any] = stream.via(Compression.deflate)
}
