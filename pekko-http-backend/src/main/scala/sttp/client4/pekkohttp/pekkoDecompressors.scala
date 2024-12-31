package sttp.client4.pekkohttp

import sttp.client4.compression.Decompressor
import sttp.model.Encodings
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.coding.Coders

object GZipPekkoDecompressor extends Decompressor[HttpResponse] {
  override val encoding: String = Encodings.Gzip
  override def apply(body: HttpResponse): HttpResponse = Coders.Gzip.decodeMessage(body)
}

object DeflatePekkoDecompressor extends Decompressor[HttpResponse] {
  override val encoding: String = Encodings.Deflate
  override def apply(body: HttpResponse): HttpResponse = Coders.Deflate.decodeMessage(body)
}
