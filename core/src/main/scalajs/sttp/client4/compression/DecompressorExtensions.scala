package sttp.client4.compression

import java.io.InputStream

trait DecompressorExtensions {
  def defaultInputStream: List[Decompressor[InputStream]] = Nil
}
