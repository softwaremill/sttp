package sttp.client4.compression

/** Defines the compressors that might be used to compress request bodies, and decompressors that might be used to
  * decompress response bodies.
  *
  * @tparam R
  *   The capabilities that the bodyies (both to compress & compressed) might use (e.g. streams).
  * @tparam B
  *   The type of the raw body (as used by the backend) that is decompressed.
  * @see
  *   [[sttp.client4.RequestOptions.decompressResponseBody]]
  * @see
  *   [[sttp.client4.RequestOptions.compressRequestBody]]
  */
case class CompressionHandlers[-R, B](
    compressors: List[Compressor[R]],
    decompressors: List[Decompressor[B]]
) {
  def addCompressor[R2 <: R](compressors: Compressor[R2]): CompressionHandlers[R2, B] =
    copy(compressors = this.compressors :+ compressors)

  def addDecompressor(decompressors: Decompressor[B]): CompressionHandlers[R, B] =
    copy(decompressors = this.decompressors :+ decompressors)
}
