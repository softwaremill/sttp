package akka.http.scaladsl.coding

import akka.http.scaladsl.model._
import java.util.zip.Deflater

/** [[Deflate]] but sets the `nowrap` flag to true instead of false.
  */
class DeflateNoWrap private[http] (
    compressionLevel: Int,
    override val messageFilter: HttpMessage => Boolean
) extends Deflate(compressionLevel, messageFilter) {
  def this(messageFilter: HttpMessage => Boolean) = {
    this(DeflateCompressor.DefaultCompressionLevel, messageFilter)
  }

  override def newCompressor = new DeflateCompressor(compressionLevel) {
    override protected lazy val deflater = new Deflater(compressionLevel, true)
  }
}
object DeflateNoWrap extends DeflateNoWrap(Encoder.DefaultFilter)
