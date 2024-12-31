package sttp.client4.impl.zio

import sttp.client4.compression.Decompressor
import sttp.model.Encodings
import sttp.capabilities.zio.ZioStreams
import zio.stream.ZPipeline
import zio.stream.ZStream
import zio.stream.ZSink

object GZipZioDecompressor extends Decompressor[ZioStreams.BinaryStream] {
  override val encoding: String = Encodings.Gzip
  override def apply(body: ZioStreams.BinaryStream): ZioStreams.BinaryStream = body.via(ZPipeline.gunzip())
}

object DeflateZioDecompressor extends Decompressor[ZioStreams.BinaryStream] {
  override val encoding: String = Encodings.Deflate
  override def apply(body: ZioStreams.BinaryStream): ZioStreams.BinaryStream =
    ZStream.scoped[Any](body.peel(ZSink.take[Byte](1))).flatMap { case (chunk, stream) =>
      val wrapped = chunk.headOption.exists(byte => (byte & 0x0f) == 0x08)
      (ZStream.fromChunk(chunk) ++ stream).via(ZPipeline.inflate(noWrap = !wrapped))
    }
}
