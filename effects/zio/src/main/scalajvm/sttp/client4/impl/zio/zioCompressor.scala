package sttp.client4.impl.zio

import sttp.client4._
import sttp.client4.compression.Compressor
import sttp.capabilities.zio.ZioStreams

import zio.stream.Stream
import sttp.client4.compression.GZipDefaultCompressor
import sttp.client4.compression.DeflateDefaultCompressor
import zio.stream.ZPipeline
import zio.stream.ZStream

trait ZioCompressor extends Compressor[ZioStreams] {
  override abstract def apply[R2 <: ZioStreams](body: GenericRequestBody[R2]): GenericRequestBody[ZioStreams] =
    body match {
      case InputStreamBody(b, _) => StreamBody(ZioStreams)(compressStream(ZStream.fromInputStream(b)))
      case StreamBody(b)         => StreamBody(ZioStreams)(compressStream(b.asInstanceOf[Stream[Throwable, Byte]]))
      case FileBody(f, _)        => StreamBody(ZioStreams)(compressStream(ZStream.fromFile(f.toFile)))
      case _                     => super.apply(body)
    }

  def compressStream(stream: Stream[Throwable, Byte]): Stream[Throwable, Byte]
}

object GZipZioCompressor extends GZipDefaultCompressor[ZioStreams] with ZioCompressor {
  def compressStream(stream: Stream[Throwable, Byte]): Stream[Throwable, Byte] = stream.via(ZPipeline.gzip())
}

object DeflateZioCompressor extends DeflateDefaultCompressor[ZioStreams] with ZioCompressor {
  def compressStream(stream: Stream[Throwable, Byte]): Stream[Throwable, Byte] = stream.via(ZPipeline.deflate())
}
