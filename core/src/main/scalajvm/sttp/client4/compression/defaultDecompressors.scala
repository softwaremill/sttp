package sttp.client4.compression

import sttp.client4.internal.SafeGZIPInputStream
import sttp.model.Encodings

import java.io.InputStream
import java.util.zip.InflaterInputStream

object GZipInputStreamDecompressor extends Decompressor[InputStream] {
  override val encoding: String = Encodings.Gzip
  override def apply(body: InputStream): InputStream = SafeGZIPInputStream.apply(body)
}

object DeflateInputStreamDecompressor extends Decompressor[InputStream] {
  override val encoding: String = Encodings.Deflate
  override def apply(body: InputStream): InputStream = new InflaterInputStream(body)
}
