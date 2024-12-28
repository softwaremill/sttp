package sttp.client4.impl.zio

import sttp.client4._
import sttp.client4.compression.Compressor
import sttp.capabilities.zio.ZioStreams

import zio.stream.Stream
import sttp.client4.compression.GZipDefaultCompressor
import sttp.client4.compression.DeflateDefaultCompressor
import zio.stream.ZPipeline
import zio.stream.ZStream

trait ZioCompressor[R <: ZioStreams] extends Compressor[R] {
  override abstract def apply(body: GenericRequestBody[R], encoding: String): GenericRequestBody[R] =
    body match {
      case InputStreamBody(b, _) => StreamBody(ZioStreams)(compressStream(ZStream.fromInputStream(b)))
      case StreamBody(b)         => StreamBody(ZioStreams)(compressStream(b.asInstanceOf[Stream[Throwable, Byte]]))
      case FileBody(f, _)        => StreamBody(ZioStreams)(compressStream(ZStream.fromFile(f.toFile)))
      case _                     => super.apply(body, encoding)
    }

  def compressStream(stream: Stream[Throwable, Byte]): Stream[Throwable, Byte]
}

class GZipZioCompressor[R <: ZioStreams] extends GZipDefaultCompressor[R] with ZioCompressor[R] {
  def compressStream(stream: Stream[Throwable, Byte]): Stream[Throwable, Byte] = stream.via(ZPipeline.gzip())
}

class DeflateZioCompressor[R <: ZioStreams] extends DeflateDefaultCompressor[R] with ZioCompressor[R] {
  def compressStream(stream: Stream[Throwable, Byte]): Stream[Throwable, Byte] = stream.via(ZPipeline.deflate())
}
