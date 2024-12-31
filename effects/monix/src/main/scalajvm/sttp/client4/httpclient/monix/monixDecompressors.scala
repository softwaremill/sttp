package sttp.client4.httpclient.monix

import sttp.capabilities.monix.MonixStreams
import sttp.model.Encodings
import sttp.client4.compression.Decompressor
import monix.reactive.compression._

object GZipMonixDecompressor extends Decompressor[MonixStreams.BinaryStream] {
  override val encoding: String = Encodings.Gzip
  override def apply(body: MonixStreams.BinaryStream): MonixStreams.BinaryStream = body.transform(gunzip())
}

object DeflateMonixDecompressor extends Decompressor[MonixStreams.BinaryStream] {
  override val encoding: String = Encodings.Deflate
  override def apply(body: MonixStreams.BinaryStream): MonixStreams.BinaryStream = body.transform(inflate())
}
