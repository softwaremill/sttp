package sttp.client

import org.scalajs.dom.webgl.Buffer

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.|

private[client] class MessageDigestCompatibility(algorithm: String) {
  private lazy val md = algorithm match {
    case "MD5" => MD5
    case _     => throw new IllegalArgumentException(s"Unsupported algorithm: $algorithm")
  }

  def digest(input: Array[Byte]): Array[Byte] = md(new String(input, "UTF-8")).getBytes("UTF-8")
}

@js.native
@JSImport("md5", JSImport.Namespace)
private object MD5 extends js.Object {
  /**
    * Returns the MD5 encoded string generated from a given string or buffer
    * @param value the given [[String]] or [[Buffer]]
    * @return the MD5 encoded string
    */
  def apply(value: String | Buffer): String = js.native
}
