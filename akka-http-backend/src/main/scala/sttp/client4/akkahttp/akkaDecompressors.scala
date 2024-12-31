package sttp.client4.akkahttp

import sttp.client4.compression.Decompressor
import sttp.model.Encodings
import akka.http.scaladsl.coding.Coders
import akka.http.scaladsl.model.HttpResponse

object GZipAkkaDecompressor extends Decompressor[HttpResponse] {
  override val encoding: String = Encodings.Gzip
  override def apply(body: HttpResponse): HttpResponse = Coders.Gzip.decodeMessage(body)
}

object DeflateAkkaDecompressor extends Decompressor[HttpResponse] {
  override val encoding: String = Encodings.Deflate
  override def apply(body: HttpResponse): HttpResponse = Coders.Deflate.decodeMessage(body)
}
