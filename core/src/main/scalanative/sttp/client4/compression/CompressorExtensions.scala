package sttp.client4.compression

trait CompressorExtensions {
  def default[R]: List[Compressor[R]] = Nil
}
