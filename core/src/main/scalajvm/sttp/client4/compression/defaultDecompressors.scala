package sttp.client4.compression

import sttp.model.Encodings
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

object GZipInputStreamDecompressor extends Decompressor[InputStream] {
  override val encoding: String = Encodings.Gzip
  override def apply(body: InputStream): InputStream = new GZIPInputStream(body)
}

object DeflateInputStreamDecompressor extends Decompressor[InputStream] {
  override val encoding: String = Encodings.Deflate
  override def apply(body: InputStream): InputStream = new InflaterInputStream(body)
}
