package sttp.client

import io.scalajs.npm.md5.MD5

private[client] class MessageDigestCompatibility(algorithm: String) {
  private lazy val md = algorithm match {
    case "MD5" => MD5
    case _     => throw new IllegalArgumentException(s"Unsupported algorithm: $algorithm")
  }

  def digest(input: Array[Byte]): Array[Byte] = md(new String(input, "UTF-8")).getBytes("UTF-8")
}
